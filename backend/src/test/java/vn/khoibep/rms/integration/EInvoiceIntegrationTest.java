package vn.khoibep.rms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import vn.khoibep.rms.IntegrationTest;

/** P4-03: e-invoice data of every paid bill, its buyer, the file for MISA and the numbers (FR-20, US-40, BR-45, BR-46). */
class EInvoiceIntegrationTest extends IntegrationTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final AtomicLong NUMBERS = new AtomicLong(10_000_000 + System.currentTimeMillis() % 1_000_000);

    @Test
    void aPaidBillHasItsInvoiceWithTheTaxOfEachRate() throws Exception {
        long food = taxCategory(8);
        long beer = taxCategory(10);
        long hotpot = dish(329_000, food);
        long bia = dish(22_000, beer);
        long orderId = openOrder(newTable().id());
        addDish(orderId, hotpot, 1);
        addDish(orderId, bia, 1);
        addDish(orderId, bia, 1);
        get("/api/orders/" + orderId + "/einvoice", as("thungan")).andExpect(status().isNotFound());

        // US-40 AC1: the two beers ordered apart are one line.
        payCash(orderId, 373_000);
        invoiceOf(orderId)
                .andExpect(jsonPath("$.invoice.total").value(373_000))
                .andExpect(jsonPath("$.invoice.beforeTax").value(344_630))
                .andExpect(jsonPath("$.invoice.taxAmount").value(28_370))
                .andExpect(jsonPath("$.invoice.paymentMethod").value("TM"))
                .andExpect(jsonPath("$.invoice.status").value("PENDING"))
                .andExpect(jsonPath("$.invoice.buyerName").value(nullValue()))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].unit").value("Phần"))
                .andExpect(jsonPath("$.lines[0].quantity").value(1))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(329_000))
                .andExpect(jsonPath("$.lines[0].beforeTax").value(304_630))
                .andExpect(jsonPath("$.lines[0].taxAmount").value(24_370))
                .andExpect(jsonPath("$.lines[1].quantity").value(2))
                .andExpect(jsonPath("$.lines[1].taxRate").value(10))
                .andExpect(jsonPath("$.taxes[0].taxRate").value(8))
                .andExpect(jsonPath("$.taxes[0].beforeTax").value(304_630))
                .andExpect(jsonPath("$.taxes[1].taxRate").value(10))
                .andExpect(jsonPath("$.taxes[1].beforeTax").value(40_000))
                .andExpect(jsonPath("$.taxes[1].taxAmount").value(4_000));
    }

    @Test
    void theRateIsTheOneInForceOnTheDayOfPayment() throws Exception {
        clock.set(LocalDateTime.of(2026, 12, 31, 23, 50));
        long food = taxCategory(8);
        put("/api/tax-categories/" + food + "/rates/2027-01-01", as("quanly"), Map.of("rate", 10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentRate").value(8))
                .andExpect(jsonPath("$.rates[*].rate").value(contains(8, 10)));
        put("/api/tax-categories/" + food + "/rates/2026-12-30", as("quanly"), Map.of("rate", 10))
                .andExpect(status().isBadRequest());
        put("/api/tax-categories/" + food + "/rates/2027-01-02", as("quanly"), Map.of("rate", 7))
                .andExpect(status().isBadRequest());
        long dishId = dish(108_000, food);

        // US-40 AC2: 00:10 on 1 January in Vietnam is still 31 December in UTC.
        long lastYear = paidOrder(dishId, 108_000);
        clock.set(LocalDateTime.of(2027, 1, 1, 0, 10));
        long newYear = paidOrder(dishId, 108_000);

        invoiceOf(lastYear).andExpect(jsonPath("$.lines[0].taxRate").value(8))
                .andExpect(jsonPath("$.invoice.beforeTax").value(100_000));
        invoiceOf(newYear).andExpect(jsonPath("$.lines[0].taxRate").value(10))
                .andExpect(jsonPath("$.invoice.beforeTax").value(98_182));
        get("/api/einvoices?from=2027-01-01&to=2027-01-01", as("quanly")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].orderId").value(hasItem((int) newYear)))
                .andExpect(jsonPath("$[*].orderId").value(not(hasItem((int) lastYear))));
    }

    @Test
    void aDishGivenFreeAndADiscountComeOffAtTheirRates() throws Exception {
        long food = taxCategory(8);
        long beer = taxCategory(10);
        long orderId = openOrder(newTable().id());
        addDish(orderId, dish(329_000, food), 1);
        long beerLine = addDish(orderId, dish(22_000, beer), 2);
        post("/api/orders/" + orderId + "/adjustments", as("quanly"),
                Map.of("type", "COMP", "orderItemId", beerLine, "reason", "PROMOTION")).andExpect(status().isCreated());
        post("/api/orders/" + orderId + "/adjustments", as("quanly"),
                Map.of("type", "DISCOUNT", "amount", 32_900, "reason", "WAIT")).andExpect(status().isCreated());

        // US-40 AC3
        payCash(orderId, 296_100);
        invoiceOf(orderId)
                .andExpect(jsonPath("$.invoice.total").value(296_100))
                .andExpect(jsonPath("$.lines[2].kind").value("DISCOUNT"))
                .andExpect(jsonPath("$.lines[2].itemName").value(containsString("Tặng: ")))
                .andExpect(jsonPath("$.lines[2].taxRate").value(10))
                .andExpect(jsonPath("$.lines[2].amount").value(44_000))
                .andExpect(jsonPath("$.lines[2].quantity").value(nullValue()))
                .andExpect(jsonPath("$.lines[3].itemName").value("Giảm giá"))
                .andExpect(jsonPath("$.lines[3].taxRate").value(8))
                .andExpect(jsonPath("$.lines[3].amount").value(32_900))
                .andExpect(jsonPath("$.taxes[0].beforeTax").value(274_167))
                .andExpect(jsonPath("$.taxes[0].taxAmount").value(21_933))
                .andExpect(jsonPath("$.taxes[1].beforeTax").value(0))
                .andExpect(jsonPath("$.taxes[1].taxAmount").value(0));
    }

    @Test
    void theDepositTakenOffCountsAsPaidByTransfer() throws Exception {
        long food = taxCategory(8);
        Map<String, Object> booking = Map.of("guestName", unique("Khách"), "phone", "0912345678",
                "reservedAt", vn(LocalDate.now(VN).plusDays(1).atTime(19, 0)), "guestCount", 2,
                "depositAmount", 100_000);
        long reservationId = readLong(post("/api/reservations", as("phucvu"), booking)
                .andExpect(status().isCreated()), "$.id");
        post("/api/reservations/" + reservationId + "/deposit/confirm", as("quanly"), null)
                .andExpect(status().isOk());
        long orderId = readLong(post("/api/reservations/" + reservationId + "/seat", as("phucvu"),
                Map.of("tableId", newTable().id())).andExpect(status().isOk()), "$.id");
        addDish(orderId, dish(329_000, food), 1);

        // US-40 AC3: the invoice is the whole bill, part of it paid by the deposit.
        payCash(orderId, 229_000);
        invoiceOf(orderId).andExpect(jsonPath("$.invoice.total").value(329_000))
                .andExpect(jsonPath("$.invoice.paymentMethod").value("TM/CK"));
    }

    @Test
    void theCashierNamesTheBuyerUntilTheInvoiceHasItsNumber() throws Exception {
        long id = paidInvoice();
        String buyer = "/api/einvoices/" + id + "/buyer";
        Map<String, Object> company = Map.of("name", "Công ty TNHH Khói Bếp", "taxCode", "0101234567",
                "address", "Phường Đống Đa, Hà Nội", "email", "ketoan@khoibep.vn");

        // US-40 AC4
        put(buyer, as("thungan"), company).andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.buyerName").value("Công ty TNHH Khói Bếp"))
                .andExpect(jsonPath("$.invoice.buyerTaxCode").value("0101234567"))
                .andExpect(jsonPath("$.invoice.buyerEmail").value("ketoan@khoibep.vn"));
        for (String taxCode : List.of("12345", "01012345678", "0101234567-01")) {
            put(buyer, as("thungan"), Map.of("name", "Công ty", "taxCode", taxCode)).andExpect(status().isBadRequest());
        }
        put(buyer, as("thungan"), Map.of("taxCode", "0101234567")).andExpect(status().isBadRequest());
        put(buyer, as("thungan"), Map.of("name", "Công ty", "email", "khong-phai-email"))
                .andExpect(status().isBadRequest());
        put(buyer, as("thungan"), Map.of("name", "Chi nhánh", "taxCode", "0101234567-001")).andExpect(status().isOk());
        put(buyer, as("thungan"), Map.of("name", "Anh Minh", "taxCode", "001099012345")).andExpect(status().isOk());
        put(buyer, as("thungan"), Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.buyerName").value(nullValue()))
                .andExpect(jsonPath("$.invoice.buyerTaxCode").value(nullValue()));
        put(buyer, as("thungan"), company).andExpect(status().isOk());

        put("/api/einvoices/" + id + "/number", as("quanly"), Map.of("symbol", "1C26MKB", "number", newNumber()))
                .andExpect(status().isOk());
        put(buyer, as("thungan"), Map.of("name", "Công ty khác")).andExpect(status().isConflict());
    }

    @Test
    void aManagerExportsTheQueueThenRecordsTheNumbers() throws Exception {
        String today = LocalDate.now(VN).toString();
        String company = unique("Công ty TNHH");
        long first = paidInvoice();
        long second = paidInvoice();
        put("/api/einvoices/" + first + "/buyer", as("thungan"), Map.of("name", company, "taxCode", "0101234567"))
                .andExpect(status().isOk());

        // US-40 AC5
        byte[] file = export(today).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("spreadsheetml")))
                .andExpect(header().string("Content-Disposition", containsString("hoa-don-" + today)))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(part(file, "xl/sharedStrings.xml")).contains(company, "0101234567", "Số thứ tự hoá đơn", "Phần");
        get("/api/einvoices?from=" + today + "&to=" + today + "&status=EXPORTED", as("quanly"))
                .andExpect(jsonPath("$[*].id").value(hasItems((int) first, (int) second)));

        String number = newNumber();
        put("/api/einvoices/" + first + "/number", as("quanly"), Map.of("symbol", "1c26mkb", "number", "000" + number))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.status").value("ISSUED"))
                .andExpect(jsonPath("$.invoice.invoiceSymbol").value("1C26MKB"))
                .andExpect(jsonPath("$.invoice.invoiceNo").value(number))
                .andExpect(jsonPath("$.invoice.issuedByName").value(notNullValue()));
        put("/api/einvoices/" + second + "/number", as("quanly"), Map.of("symbol", "1C26MKB", "number", number))
                .andExpect(status().isConflict());
        String corrected = newNumber();
        put("/api/einvoices/" + first + "/number", as("quanly"), Map.of("symbol", "1C26MKB", "number", corrected))
                .andExpect(status().isOk()).andExpect(jsonPath("$.invoice.invoiceNo").value(corrected));
        for (Map<String, Object> wrong : List.<Map<String, Object>>of(Map.of("symbol", "ABC", "number", "1"),
                Map.of("symbol", "1C26MKB", "number", "0"), Map.of("symbol", "1C26MKB", "number", "123456789"))) {
            put("/api/einvoices/" + second + "/number", as("quanly"), wrong).andExpect(status().isBadRequest());
        }

        // An issued invoice leaves the queue to export.
        assertThat(part(export(today).andReturn().getResponse().getContentAsByteArray(), "xl/sharedStrings.xml"))
                .doesNotContain(company);
        get("/api/einvoices?from=" + today + "&to=" + today + "&status=ISSUED", as("quanly"))
                .andExpect(jsonPath("$[*].id").value(hasItem((int) first)))
                .andExpect(jsonPath("$[*].id").value(not(hasItem((int) second))));
        get("/api/einvoices/" + first, as("quanly")).andExpect(jsonPath("$.lines.length()").value(1));
        String later = LocalDate.now(VN).plusDays(31).toString();
        get("/api/einvoices?from=" + today + "&to=" + later, as("quanly")).andExpect(status().isBadRequest());
    }

    @Test
    void aDishTakesTheFirstTaxCategoryUnlessOneIsChosen() throws Exception {
        List<Number> categories = read(get("/api/tax-categories", as("quanly")).andExpect(status().isOk()), "$[*].id");
        long categoryId = readLong(get("/api/categories", as("quanly")), "$[0].id");
        post("/api/menu-items", as("quanly"), Map.of("categoryId", categoryId, "name", unique("Món"), "price", 10_000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taxCategoryId").value(categories.getFirst().longValue()));

        long beer = taxCategory(10);
        long id = readLong(post("/api/menu-items", as("quanly"), Map.of("categoryId", categoryId, "name",
                unique("Bia"), "price", 20_000, "taxCategoryId", beer)).andExpect(status().isCreated()), "$.id");
        put("/api/menu-items/" + id, as("quanly"), Map.of("categoryId", categoryId, "name", unique("Bia"), "price",
                25_000)).andExpect(status().isOk()).andExpect(jsonPath("$.taxCategoryId").value(beer));
        post("/api/menu-items", as("quanly"), Map.of("categoryId", categoryId, "name", unique("Món"), "price",
                10_000, "taxCategoryId", Long.MAX_VALUE)).andExpect(status().isNotFound());
        post("/api/tax-categories", as("quanly"), Map.of("name", "Ăn uống", "rate", 8)).andExpect(status().isConflict());
        post("/api/tax-categories", as("quanly"), Map.of("name", unique("Thuế"), "rate", 7))
                .andExpect(status().isBadRequest());

        // V22 put the beer of the demo menu in its own category.
        get("/api/menu-items", as("phucvu"))
                .andExpect(jsonPath("$[?(@.name == 'Bia Hà Nội')].taxCategoryName").value(hasItem("Rượu, bia")));
    }

    @Test
    void onlyManagersKeepTheQueueAndCashiersOnlyNameTheBuyer() throws Exception {
        long id = paidInvoice();
        long orderId = readLong(get("/api/einvoices/" + id, as("quanly")), "$.invoice.orderId");
        String today = LocalDate.now(VN).toString();

        // US-40 AC5
        for (String user : List.of("phucvu", "bep", "thungan")) {
            get("/api/einvoices?from=" + today + "&to=" + today, as(user)).andExpect(status().isForbidden());
            get("/api/einvoices/" + id, as(user)).andExpect(status().isForbidden());
            post("/api/einvoices/export", as(user), Map.of("from", today, "to", today))
                    .andExpect(status().isForbidden());
            put("/api/einvoices/" + id + "/number", as(user), Map.of("symbol", "1C26MKB", "number", "1"))
                    .andExpect(status().isForbidden());
            get("/api/tax-categories", as(user)).andExpect(status().isForbidden());
        }
        for (String user : List.of("phucvu", "bep")) {
            get("/api/orders/" + orderId + "/einvoice", as(user)).andExpect(status().isForbidden());
            put("/api/einvoices/" + id + "/buyer", as(user), Map.of("name", "X")).andExpect(status().isForbidden());
        }
        get("/api/einvoices/" + id, as("admin")).andExpect(status().isOk());
    }

    /** A tax category of this test only, its rate in force from today. */
    private long taxCategory(int rate) throws Exception {
        return readLong(post("/api/tax-categories", as("quanly"), Map.of("name", unique("Thuế"), "rate", rate))
                .andExpect(status().isCreated()), "$.id");
    }

    private long dish(long price, long taxCategoryId) throws Exception {
        long categoryId = readLong(get("/api/categories", as("quanly")), "$[0].id");
        return readLong(post("/api/menu-items", as("quanly"), Map.of("categoryId", categoryId, "name",
                unique("Món"), "price", price, "taxCategoryId", taxCategoryId)).andExpect(status().isCreated()), "$.id");
    }

    private long paidOrder(long dishId, long price) throws Exception {
        long orderId = openOrder(newTable().id());
        addDish(orderId, dishId, 1);
        payCash(orderId, price);
        return orderId;
    }

    /** A bill of one dish of the first tax category, paid in cash; the id of its invoice. */
    private long paidInvoice() throws Exception {
        return readLong(invoiceOf(paidOrder(newDish(50_000), 50_000)), "$.invoice.id");
    }

    private void payCash(long orderId, long amount) throws Exception {
        post("/api/orders/" + orderId + "/payments/cash", as("thungan"), Map.of("receivedAmount", amount))
                .andExpect(status().isOk());
    }

    private ResultActions invoiceOf(long orderId) throws Exception {
        return get("/api/orders/" + orderId + "/einvoice", as("thungan")).andExpect(status().isOk());
    }

    private ResultActions export(String day) throws Exception {
        return post("/api/einvoices/export", as("quanly"), Map.of("from", day, "to", day));
    }

    /** An invoice number no other test records. */
    private static String newNumber() {
        return String.valueOf(NUMBERS.incrementAndGet());
    }

    private static String part(byte[] xlsx, String name) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().equals(name)) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("No " + name + " in the file");
    }
}
