package vn.khoibep.rms.model;

import java.math.BigDecimal;
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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;


/** Goods bought from a supplier, with what was paid (FR-09.6). Never edited or deleted once saved (BR-37). */
@Entity
@Table(name = "goods_receipt")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoodsReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    private String note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private Employee createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<ReceiptLine> lines = new ArrayList<>();

    public GoodsReceipt(Supplier supplier, String note, Employee createdBy, Instant createdAt) {
        this.supplier = supplier;
        this.note = note;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public ReceiptLine addLine(InventoryItem item, BigDecimal quantity, long unitPrice) {
        ReceiptLine line = new ReceiptLine(this, item, quantity, unitPrice);
        lines.add(line);
        return line;
    }

    /** What the receipt cost: the sum of its lines. */
    public long total() {
        return lines.stream().mapToLong(ReceiptLine::lineTotal).sum();
    }
}
