package vn.khoibep.rms.menu.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** BR-45: the VAT rate of a tax category from a day on, Vietnam time, in percent. */
@Entity
@Table(name = "tax_rate")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaxRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_category_id")
    private TaxCategory category;

    @Column(nullable = false)
    private int rate;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    public TaxRate(TaxCategory category, int rate, LocalDate effectiveFrom) {
        this.category = category;
        this.rate = rate;
        this.effectiveFrom = effectiveFrom;
    }

    public void change(int newRate) {
        rate = newRate;
    }
}
