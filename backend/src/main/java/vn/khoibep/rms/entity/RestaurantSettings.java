package vn.khoibep.rms.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Single row (id = 1) created by the V2 migration. */
@Entity
@Table(name = "restaurant_settings")
@Getter
@Setter
@NoArgsConstructor
public class RestaurantSettings {

    public static final int ID = 1;

    @Id
    private Integer id;

    @Column(nullable = false)
    private String name;

    private String address;

    private String phone;

    /** Bank BIN or VietQR short name, for example 970436 or vietcombank. */
    private String bankCode;

    private String bankAccountNo;

    private String bankAccountName;

    /** A dish this many minutes in the kitchen is late and shown in red (BR-28). */
    @Column(nullable = false)
    private int waitAlertMinutes = 15;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public boolean hasBankAccount() {
        return isSet(bankCode) && isSet(bankAccountNo) && isSet(bankAccountName);
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
