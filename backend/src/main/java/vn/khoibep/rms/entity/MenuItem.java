package vn.khoibep.rms.entity;

import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import vn.khoibep.rms.enums.Channel;

@Entity
@Table(name = "menu_item")
@Getter
@Setter
@NoArgsConstructor
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private String name;

    /** VND, VAT included. */
    @Column(nullable = false)
    private long price;

    private String description;

    /** False when the kitchen has run out (FR-03.3). */
    @Column(nullable = false)
    private boolean available = true;

    /** BR-45: what share of the price is VAT, by the rate of the category on the day the bill is paid. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_category_id")
    private TaxCategory taxCategory;

    /** BR-47: the price on each delivery app, VAT included; a dish without one is not sold on that app. */
    @ElementCollection
    @CollectionTable(name = "menu_item_app_price", joinColumns = @JoinColumn(name = "menu_item_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "channel")
    @Column(name = "price")
    private Map<Channel, Long> appPrices = new HashMap<>();
}
