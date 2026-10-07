package vn.khoibep.rms.model;

import java.time.Instant;

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
import lombok.Getter;
import lombok.NoArgsConstructor;

import vn.khoibep.rms.enums.ServiceRequestType;

/**
 * A guest calling a waiter or asking for the bill (FR-06.6). It waits until a waiter takes it (FR-06.7); rows are
 * created by {@link ServiceRequestRepository#insertOpenIfAbsent} so that BR-29 holds under concurrent taps.
 */
@Entity
@Table(name = "service_request")
@Getter
@NoArgsConstructor
public class ServiceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_id")
    private DiningTable table;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServiceRequestType type;

    @Column(nullable = false)
    private Instant createdAt;

    /** The employee who took the call; null while it waits. */
    private Long handledBy;

    private Instant handledAt;

    public boolean isOpen() {
        return handledAt == null;
    }

    public void handle(Long employeeId, Instant at) {
        handledBy = employeeId;
        handledAt = at;
    }
}
