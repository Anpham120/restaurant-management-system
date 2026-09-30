package vn.bnn.rms.common;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Publishes realtime notices. They leave the server only after the database transaction commits. */
@Component
@RequiredArgsConstructor
public class RealtimeEvents {

    public static final String STAFF_TOPIC = "/topic/staff";
    public static final String GUEST_TOPIC_PREFIX = "/topic/guest/";
    public static final String MENU_TOPIC = "/topic/menu";

    private final ApplicationEventPublisher publisher;
    private final SimpMessagingTemplate messaging;

    public void orderChanged(Long orderId, Long tableId, String guestToken) {
        publisher.publishEvent(new RealtimeEvent(RealtimeEvent.ORDER_CHANGED, orderId, tableId, guestToken));
    }

    public void paymentPaid(Long orderId, Long tableId, String guestToken) {
        publisher.publishEvent(new RealtimeEvent(RealtimeEvent.PAYMENT_PAID, orderId, tableId, guestToken));
    }

    public void staffNotice(String type) {
        publisher.publishEvent(new RealtimeEvent(type, null, null, null));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void relay(RealtimeEvent event) {
        RealtimeEvent.Message message = event.toMessage();
        messaging.convertAndSend(STAFF_TOPIC, message);
        if (event.guestToken() != null) {
            messaging.convertAndSend(GUEST_TOPIC_PREFIX + event.guestToken(), message);
        }
        if (RealtimeEvent.MENU_CHANGED.equals(event.type())) {
            messaging.convertAndSend(MENU_TOPIC, message);
        }
    }
}
