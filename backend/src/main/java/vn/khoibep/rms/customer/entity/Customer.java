package vn.khoibep.rms.customer.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import vn.khoibep.rms.customer.enums.ConsentChannel;

/** A guest known by a normalised phone number (BR-44). Never deleted. Loaded in batches for lists of orders. */
@Entity
@Table(name = "customer")
@BatchSize(size = 50)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String phone;

    private String name;

    private String note;

    /** The latest agreement to hear from the restaurant; optedOutAt is a refusal since then. */
    @Enumerated(EnumType.STRING)
    private ConsentChannel consentChannel;

    private Instant consentAt;

    private String consentSource;

    private Instant optedOutAt;

    @Column(nullable = false)
    private Instant createdAt;

    public Customer(String phone, String name, Instant createdAt) {
        this.phone = phone;
        this.name = name;
        this.createdAt = createdAt;
    }

    public void describe(String name, String note) {
        this.name = name;
        this.note = note;
    }

    /**
     * BR-44: a guest may be sent messages when they agreed and have not refused since. The order of the calls decides,
     * not the times recorded: the server clock may be set back between two of them.
     */
    public boolean mayContact() {
        return consentAt != null && optedOutAt == null;
    }

    /** A new agreement replaces the last one and lifts a refusal. */
    public void consent(ConsentChannel channel, String source, Instant at) {
        consentChannel = channel;
        consentSource = source;
        consentAt = at;
        optedOutAt = null;
    }

    /** No messages from now on; the last agreement stays as evidence of what the guest had said. */
    public void optOut(Instant at) {
        optedOutAt = at;
    }
}
