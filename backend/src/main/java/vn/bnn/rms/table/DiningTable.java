package vn.bnn.rms.table;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A table. Whether it is free is not stored: it is free when it has no open order (BR-04). */
@Entity
@Table(name = "dining_table")
@Getter
@Setter
@NoArgsConstructor
public class DiningTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String area;

    @Column(nullable = false)
    private int seats;

    /** Secret printed in the table QR code (BR-09). */
    @Column(nullable = false, unique = true)
    private String qrToken;
}
