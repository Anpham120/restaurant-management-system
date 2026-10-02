package vn.khoibep.rms.einvoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import vn.khoibep.rms.einvoice.enums.LineKind;

/** A line of an e-invoice as it stood when the bill was paid (BR-46). Amounts in VND, VAT included like the menu. */
@Entity
@Table(name = "einvoice_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EInvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "einvoice_id")
    private EInvoice invoice;

    @Column(nullable = false)
    private int lineNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LineKind kind;

    @Column(nullable = false)
    private String itemName;

    /** Null on a discount line, as are the quantity and the unit price. */
    private String unit;

    private Integer quantity;

    private Long unitPrice;

    @Column(nullable = false)
    private int taxRate;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private long beforeTax;

    @Column(nullable = false)
    private long taxAmount;

    private EInvoiceLine(LineKind kind, String itemName, int taxRate, long amount) {
        this.kind = kind;
        this.itemName = itemName;
        this.taxRate = taxRate;
        this.amount = amount;
        this.beforeTax = beforeTax(amount, taxRate);
        this.taxAmount = amount - beforeTax;
    }

    /** Dishes of the same name, price and rate. */
    public static EInvoiceLine goods(String itemName, String unit, int quantity, long unitPrice, int taxRate) {
        EInvoiceLine line = new EInvoiceLine(LineKind.GOODS, itemName, taxRate, unitPrice * quantity);
        line.unit = unit;
        line.quantity = quantity;
        line.unitPrice = unitPrice;
        return line;
    }

    public static EInvoiceLine discount(String itemName, long amount, int taxRate) {
        return new EInvoiceLine(LineKind.DISCOUNT, itemName, taxRate, amount);
    }

    /** BR-46: the part of an amount with VAT included that is not tax, rounded to the đồng, a half up. */
    public static long beforeTax(long amount, int taxRate) {
        return (amount * 100 + (100 + taxRate) / 2) / (100 + taxRate);
    }

    void attach(EInvoice owner, int number) {
        invoice = owner;
        lineNo = number;
    }
}
