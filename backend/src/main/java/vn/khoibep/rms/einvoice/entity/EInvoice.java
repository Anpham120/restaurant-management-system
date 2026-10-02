package vn.khoibep.rms.einvoice.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.einvoice.enums.EInvoiceStatus;
import vn.khoibep.rms.einvoice.enums.LineKind;
import vn.khoibep.rms.employee.entity.Employee;
import vn.khoibep.rms.order.entity.Order;

/**
 * The e-invoice data of an order paid in full (FR-20.2, BR-46), kept apart from the bill: it waits to be exported, then
 * is issued on MISA under a symbol and number. Never deleted. Amounts in VND; total includes VAT.
 */
@Entity
@Table(name = "einvoice")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(nullable = false)
    private Instant invoiceDate;

    /** TM, CK or TM/CK, as MISA writes them. */
    @Column(nullable = false)
    private String paymentMethod;

    /** Null for a walk-in guest, as are the other details of the buyer. */
    private String buyerName;

    private String buyerTaxCode;

    private String buyerAddress;

    private String buyerEmail;

    @Column(nullable = false)
    private long beforeTax;

    @Column(nullable = false)
    private long taxAmount;

    @Column(nullable = false)
    private long total;

    private Instant exportedAt;

    private String invoiceSymbol;

    private String invoiceNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by")
    private Employee issuedBy;

    private Instant issuedAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.PERSIST)
    @OrderBy("lineNo")
    private List<EInvoiceLine> lines = new ArrayList<>();

    public EInvoice(Order order, Instant invoiceDate, String paymentMethod) {
        this.order = order;
        this.invoiceDate = invoiceDate;
        this.paymentMethod = paymentMethod;
    }

    /** Numbers the line and keeps the totals the sum of the lines, the discounts taken off. */
    public void add(EInvoiceLine line) {
        line.attach(this, lines.size() + 1);
        lines.add(line);
        long sign = line.getKind() == LineKind.DISCOUNT ? -1 : 1;
        beforeTax += sign * line.getBeforeTax();
        taxAmount += sign * line.getTaxAmount();
        total += sign * line.getAmount();
    }

    public EInvoiceStatus status() {
        if (invoiceNo != null) {
            return EInvoiceStatus.ISSUED;
        }
        return exportedAt != null ? EInvoiceStatus.EXPORTED : EInvoiceStatus.PENDING;
    }

    /** BR-46: the buyer can change until the invoice has its number; after that only on MISA. */
    public void describeBuyer(String name, String taxCode, String address, String email) {
        if (invoiceNo != null) {
            throw ApiException.conflict("Hoá đơn đã có số, sửa người mua trên MISA");
        }
        buyerName = name;
        buyerTaxCode = taxCode;
        buyerAddress = address;
        buyerEmail = email;
    }

    public void exported(Instant at) {
        exportedAt = at;
    }

    /** BR-46: the symbol and number it was issued under on MISA; recording them again corrects them. */
    public void issue(String symbol, String number, Employee by, Instant at) {
        invoiceSymbol = symbol;
        invoiceNo = number;
        issuedBy = by;
        issuedAt = at;
    }
}
