package vn.bnn.rms.order;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.bnn.rms.order.ItemStatus.CANCELLED;
import static vn.bnn.rms.order.ItemStatus.COOKING;
import static vn.bnn.rms.order.ItemStatus.PENDING;
import static vn.bnn.rms.order.ItemStatus.READY;
import static vn.bnn.rms.order.ItemStatus.SERVED;
import static vn.bnn.rms.order.ItemStatus.WAITING;

import org.junit.jupiter.api.Test;

/** BR-07, BR-08, BR-12. */
class ItemStatusTest {

    @Test
    void movesForwardOneStepAtATime() {
        assertThat(PENDING.canMoveTo(WAITING)).isTrue();
        assertThat(WAITING.canMoveTo(COOKING)).isTrue();
        assertThat(COOKING.canMoveTo(READY)).isTrue();
        assertThat(READY.canMoveTo(SERVED)).isTrue();

        assertThat(WAITING.canMoveTo(READY)).isFalse();
        assertThat(PENDING.canMoveTo(COOKING)).isFalse();
        assertThat(READY.canMoveTo(COOKING)).isFalse();
        assertThat(SERVED.canMoveTo(READY)).isFalse();
    }

    @Test
    void canBeCancelledUntilServed() {
        assertThat(PENDING.canMoveTo(CANCELLED)).isTrue();
        assertThat(WAITING.canMoveTo(CANCELLED)).isTrue();
        assertThat(COOKING.canMoveTo(CANCELLED)).isTrue();
        assertThat(READY.canMoveTo(CANCELLED)).isTrue();
        assertThat(SERVED.canMoveTo(CANCELLED)).isFalse();
        assertThat(CANCELLED.canMoveTo(WAITING)).isFalse();
    }

    @Test
    void onlyConfirmedAndNotCancelledDishesAreCharged() {
        assertThat(PENDING.isBillable()).isFalse();
        assertThat(CANCELLED.isBillable()).isFalse();
        assertThat(WAITING.isBillable()).isTrue();
        assertThat(SERVED.isBillable()).isTrue();
    }
}
