package vn.khoibep.rms.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.Principal;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.TestingAuthenticationToken;

/** Who may listen to which realtime topic (NFR-05). */
class StompAuthInterceptorTest {

    // Token decoding is only used on CONNECT, which these tests do not send.
    private final StompAuthInterceptor interceptor = new StompAuthInterceptor(null, null);

    @Test
    void guestsMayListenToTheirTableAndToTheMenu() {
        assertThatCode(() -> send(StompCommand.SUBSCRIBE, "/topic/guest/abc123", null)).doesNotThrowAnyException();
        assertThatCode(() -> send(StompCommand.SUBSCRIBE, "/topic/menu", null)).doesNotThrowAnyException();
    }

    @Test
    void staffTopicNeedsASignedInEmployee() {
        assertThatThrownBy(() -> send(StompCommand.SUBSCRIBE, "/topic/staff", null))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatCode(() -> send(StompCommand.SUBSCRIBE, "/topic/staff", new TestingAuthenticationToken("1", null)))
                .doesNotThrowAnyException();
    }

    @Test
    void otherTopicsAndClientMessagesAreRejected() {
        assertThatThrownBy(() -> send(StompCommand.SUBSCRIBE, "/topic/other", new TestingAuthenticationToken("1", null)))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> send(StompCommand.SEND, "/app/anything", null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    private void send(StompCommand command, String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(user);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        interceptor.preSend(message, null);
    }
}
