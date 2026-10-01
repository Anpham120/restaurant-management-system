package vn.khoibep.rms.config;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import vn.khoibep.rms.common.realtime.RealtimeEvents;

/**
 * Staff connect with "Authorization: Bearer ..." in the CONNECT frame and may listen to /topic/staff.
 * Guests connect without a token and may only listen to their table topic and the menu topic.
 * Clients never send messages to the server over STOMP.
 */
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;
    private final ActiveEmployeeJwtConverter jwtConverter;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> {
                String header = accessor.getFirstNativeHeader("Authorization");
                if (header != null && header.startsWith("Bearer ")) {
                    accessor.setUser(jwtConverter.convert(jwtDecoder.decode(header.substring(7))));
                }
            }
            case SUBSCRIBE -> checkSubscription(accessor);
            case SEND -> throw new MessageDeliveryException("Clients cannot send messages");
            default -> {
                // DISCONNECT, UNSUBSCRIBE, heartbeats: nothing to check
            }
        }
        return message;
    }

    private void checkSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new MessageDeliveryException("Missing destination");
        }
        boolean publicTopic = destination.startsWith(RealtimeEvents.GUEST_TOPIC_PREFIX)
                || destination.equals(RealtimeEvents.MENU_TOPIC);
        boolean staffTopic = destination.equals(RealtimeEvents.STAFF_TOPIC) && accessor.getUser() != null;
        if (!publicTopic && !staffTopic) {
            throw new MessageDeliveryException("Not allowed to subscribe to " + destination);
        }
    }
}
