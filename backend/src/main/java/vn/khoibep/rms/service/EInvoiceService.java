package vn.khoibep.rms.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.common.security.CurrentUser;
import vn.khoibep.rms.common.util.Xlsx;
import vn.khoibep.rms.dto.EInvoiceDtos.BuyerRequest;
import vn.khoibep.rms.dto.EInvoiceDtos.EInvoiceDetailDto;
import vn.khoibep.rms.dto.EInvoiceDtos.EInvoiceDto;
import vn.khoibep.rms.dto.EInvoiceDtos.NumberRequest;
import vn.khoibep.rms.entity.Adjustment;
import vn.khoibep.rms.entity.EInvoice;
import vn.khoibep.rms.entity.EInvoiceLine;
import vn.khoibep.rms.entity.Order;
import vn.khoibep.rms.entity.OrderItem;
import vn.khoibep.rms.entity.Payment;
import vn.khoibep.rms.enums.AdjustmentType;
import vn.khoibep.rms.enums.EInvoiceStatus;
import vn.khoibep.rms.enums.LineKind;
import vn.khoibep.rms.enums.PaymentMethod;
import vn.khoibep.rms.enums.PaymentStatus;
import vn.khoibep.rms.repository.EInvoiceRepository;
import vn.khoibep.rms.repository.EmployeeRepository;
import vn.khoibep.rms.service.TaxService.TaxRates;

/** FR-20.2 → FR-20.4, BR-46: the e-invoice of every bill paid in full, its buyer, the file for MISA, its number. */
@Service
@RequiredArgsConstructor
public class EInvoiceService {

    /** The unit of every dish (BR-46); the accountant changes it on MISA where another one fits better. */
    static final String UNIT = "Phần";

    /** Lists and exports cover at most this many days. */
    private static final int MAX_DAYS = 31;

    private static final Pattern TAX_CODE = Pattern.compile("[0-9]{10}(-[0-9]{3})?|[0-9]{12}");
    private static final Pattern SYMBOL = Pattern.compile("[1-9][CK][0-9]{2}[A-Z][A-Z0-9]{2}");
    /** Leading zeros are dropped, so "00000123" and "123" are the same number. */
    private static final Pattern NUMBER = Pattern.compile("0*([1-9][0-9]{0,7})");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** The columns of the file, one row per line; the lines of an invoice share its sequence number, as MISA asks. */
    private static final List<String> HEADER = List.of("Số thứ tự hoá đơn", "Ngày hoá đơn", "Mã đơn",
            "Tên người mua", "Mã số thuế", "Địa chỉ", "Email", "Hình thức thanh toán", "Tính chất",
            "Tên hàng hoá, dịch vụ", "Đơn vị tính", "Số lượng", "Đơn giá gồm thuế", "Thành tiền gồm thuế",
            "Thuế suất", "Thành tiền chưa thuế", "Tiền thuế GTGT");

    private record Goods(String name, long unitPrice, int rate) {
    }

    private record Range(Instant from, Instant to) {
    }

    private final EInvoiceRepository invoices;
    private final EmployeeRepository employees;
    private final TaxService taxes;
    private final CurrentUser currentUser;
    private final Clock clock;

    /** BR-46: the e-invoice of an order just paid in full, dated now, at the rates in force today in Vietnam. */
    @Transactional
    public void queue(Order order) {
        Instant now = clock.instant();
        LocalDate day = LocalDate.ofInstant(now, clock.getZone());
        TaxRates rates = taxes.rates();
        EInvoice invoice = new EInvoice(order, now, paymentMethod(order));

        // The dishes billed, one line for the same name, price and rate, and what each rate comes to.
        Map<Goods, Integer> goods = new LinkedHashMap<>();
        Map<Integer, Long> left = new TreeMap<>();
        for (OrderItem i : order.getItems()) {
            if (i.getStatus().isBillable()) {
                int rate = rates.on(i.getMenuItem().getTaxCategory().getId(), day);
                goods.merge(new Goods(i.getItemName(), i.getUnitPrice(), rate), i.getQuantity(), Integer::sum);
                left.merge(rate, i.lineTotal(), Long::sum);
            }
        }
        goods.forEach((g, quantity) -> invoice.add(EInvoiceLine.goods(g.name(), UNIT, quantity, g.unitPrice(),
                g.rate())));

        // A dish given free comes off at its own rate, then a discount on the bill over what each rate has left;
        // never more than the dishes, as on the bill (BR-12).
        long discount = Math.min(order.discountTotal(), order.subtotal());
        for (Adjustment a : order.getAdjustments()) {
            if (a.isInEffect() && a.getType() == AdjustmentType.COMP && a.getItem().getStatus().isBillable()) {
                int rate = rates.on(a.getItem().getMenuItem().getTaxCategory().getId(), day);
                long amount = Math.min(Math.min(a.getAmount(), left.get(rate)), discount);
                if (amount > 0) {
                    invoice.add(EInvoiceLine.discount("Tặng: " + a.getItem().getItemName(), amount, rate));
                    left.merge(rate, -amount, Long::sum);
                    discount -= amount;
                }
            }
        }
        spread(discount, left).forEach((rate, amount) -> {
            if (amount > 0) {
                invoice.add(EInvoiceLine.discount("Giảm giá", amount, rate));
            }
        });
        invoices.save(invoice);
    }

    /** FR-20.4: the queue of some days, oldest first; status narrows it. */
    @Transactional(readOnly = true)
    public List<EInvoiceDto> list(LocalDate from, LocalDate to, EInvoiceStatus status) {
        Range range = range(from, to);
        return invoices.findBetween(range.from(), range.to()).stream()
                .filter(i -> status == null || i.status() == status).map(EInvoiceDto::from).toList();
    }

    @Transactional(readOnly = true)
    public EInvoiceDetailDto get(Long id) {
        return EInvoiceDetailDto.from(invoices.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy hoá đơn")));
    }

    @Transactional(readOnly = true)
    public EInvoiceDetailDto ofOrder(Long orderId) {
        return EInvoiceDetailDto.from(invoices.findByOrderId(orderId)
                .orElseThrow(() -> ApiException.notFound("Đơn chưa thu đủ nên chưa có hoá đơn")));
    }

    /** FR-20.3, BR-46: who buys; all blank is a walk-in guest. */
    @Transactional
    public EInvoiceDetailDto describeBuyer(Long id, BuyerRequest request) {
        String name = blankToNull(request.name());
        String taxCode = blankToNull(request.taxCode());
        String address = blankToNull(request.address());
        String email = blankToNull(request.email());
        if (name == null && (taxCode != null || address != null || email != null)) {
            throw ApiException.badRequest("Nhập tên người mua");
        }
        if (taxCode != null && !TAX_CODE.matcher(taxCode).matches()) {
            throw ApiException.badRequest("Mã số thuế có 10 số, 10 số và 3 số chi nhánh (0101234567-001), hoặc 12 số");
        }
        EInvoice invoice = lock(id);
        invoice.describeBuyer(name, taxCode, address, email);
        return EInvoiceDetailDto.from(invoice);
    }

    /** FR-20.4: the invoices of those days still without a number, as an Excel sheet for MISA; they count as exported. */
    @Transactional
    public byte[] export(LocalDate from, LocalDate to) {
        Range range = range(from, to);
        Instant now = clock.instant();
        List<List<?>> rows = new ArrayList<>();
        rows.add(HEADER);
        int sequence = 0;
        for (EInvoice invoice : invoices.findNotIssuedWithLines(range.from(), range.to())) {
            sequence++;
            // Fetched with a join, the lines may come in any order.
            for (EInvoiceLine line : invoice.getLines().stream()
                    .sorted(Comparator.comparingInt(EInvoiceLine::getLineNo)).toList()) {
                rows.add(row(sequence, invoice, line));
            }
            invoice.exported(now);
        }
        return Xlsx.workbook("HoaDon", rows);
    }

    /** FR-20.4, BR-46: the symbol and number the invoice was issued under on MISA. */
    @Transactional
    public EInvoiceDetailDto issue(Long id, NumberRequest request) {
        String symbol = request.symbol().trim().toUpperCase(Locale.ROOT);
        if (!SYMBOL.matcher(symbol).matches()) {
            throw ApiException.badRequest("Ký hiệu có 7 ký tự, ví dụ 1C26MKB");
        }
        Matcher number = NUMBER.matcher(request.number().trim());
        if (!number.matches()) {
            throw ApiException.badRequest("Số hoá đơn có từ 1 đến 8 chữ số");
        }
        EInvoice invoice = lock(id);
        if (invoices.existsByInvoiceSymbolAndInvoiceNoAndIdNot(symbol, number.group(1), id)) {
            throw ApiException.conflict("Ký hiệu và số này đã ghi cho hoá đơn khác");
        }
        invoice.issue(symbol, number.group(1), employees.getReferenceById(currentUser.id()), clock.instant());
        return EInvoiceDetailDto.from(invoice);
    }

    /**
     * BR-46: an amount over the tax rates in proportion to what each has left, each rate rounded down and the đồng
     * still owed going one by one to the largest remainders, the higher rate first when they are equal.
     */
    static Map<Integer, Long> spread(long amount, Map<Integer, Long> base) {
        Map<Integer, Long> shares = new TreeMap<>();
        long total = base.values().stream().mapToLong(Long::longValue).sum();
        if (amount <= 0 || total <= 0) {
            return shares;
        }
        Map<Integer, Long> remainders = new HashMap<>();
        long given = 0;
        for (Map.Entry<Integer, Long> e : base.entrySet()) {
            long exact = Math.multiplyExact(amount, e.getValue());
            shares.put(e.getKey(), exact / total);
            remainders.put(e.getKey(), exact % total);
            given += exact / total;
        }
        Comparator<Integer> largestRemainder = Comparator.comparing((Integer rate) -> remainders.get(rate)).reversed();
        List<Integer> byRemainder = remainders.keySet().stream()
                .sorted(largestRemainder.thenComparing(Comparator.<Integer>reverseOrder()))
                .toList();
        for (int k = 0; k < amount - given; k++) {
            shares.merge(byRemainder.get(k), 1L, Long::sum);
        }
        return shares;
    }

    /**
     * TM for cash only, CK for transfers only (a deposit, and an app paying for its order, are transfers too), TM/CK for
     * both, as MISA writes them.
     */
    private static String paymentMethod(Order order) {
        boolean cash = false;
        boolean transfer = order.depositCredit() > 0;
        for (Payment p : order.getPayments()) {
            if (p.getStatus() == PaymentStatus.PAID && p.getAmount() > 0) {
                cash |= p.getMethod() == PaymentMethod.CASH;
                transfer |= p.getMethod() != PaymentMethod.CASH;
            }
        }
        return cash && transfer ? "TM/CK" : transfer ? "CK" : "TM";
    }

    private List<Object> row(int sequence, EInvoice invoice, EInvoiceLine line) {
        boolean goods = line.getKind() == LineKind.GOODS;
        return Arrays.asList(sequence, DAY.format(invoice.getInvoiceDate().atZone(clock.getZone())),
                invoice.getOrder().getId(), invoice.getBuyerName(), invoice.getBuyerTaxCode(),
                invoice.getBuyerAddress(), invoice.getBuyerEmail(), invoice.getPaymentMethod(),
                goods ? "Hàng hoá, dịch vụ" : "Chiết khấu thương mại", line.getItemName(), line.getUnit(),
                line.getQuantity(), line.getUnitPrice(), line.getAmount(), line.getTaxRate() + "%",
                line.getBeforeTax(), line.getTaxAmount());
    }

    /** Days in Vietnam, both included, at most {@link #MAX_DAYS}. */
    private Range range(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw ApiException.badRequest("Chọn từ 1 đến " + MAX_DAYS + " ngày");
        }
        return new Range(from.atStartOfDay(clock.getZone()).toInstant(),
                to.plusDays(1).atStartOfDay(clock.getZone()).toInstant());
    }

    private EInvoice lock(Long id) {
        return invoices.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy hoá đơn"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
