package vn.khoibep.rms.entity;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A shift template such as "Sáng 07:00–14:00". Never crosses midnight (BR-23). */
@Entity
@Table(name = "work_shift")
@Getter
@Setter
@NoArgsConstructor
public class WorkShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private boolean active = true;

    /** Two shifts overlap when each starts before the other ends; back-to-back shifts do not. */
    public boolean overlaps(WorkShift other) {
        return startTime.isBefore(other.endTime) && other.startTime.isBefore(endTime);
    }
}
