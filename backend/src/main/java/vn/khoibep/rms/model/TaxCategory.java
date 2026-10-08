package vn.khoibep.rms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** BR-45: a kind of dish for VAT, such as food and drink, or beer and spirits. Its rate depends on the day. */
@Entity
@Table(name = "tax_category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaxCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    public TaxCategory(String name) {
        this.name = name;
    }
}
