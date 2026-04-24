package ch.uzh.ifi.hase.soprafs26.config;

import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StompAuthChannelInterceptorTest {

    private UserRepository userRepository;
    private StompAuthChannelInterceptor interceptor;
    private MessageChannel channel;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        interceptor = new StompAuthChannelInterceptor(userRepository);
        channel = mock(MessageChannel.class);
    }

    private Message<?> buildConnectMessage(String token) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (token != null) {
            accessor.addNativeHeader("token", token);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void connect_validToken_allowsMessage() {
        User user = new User();
        user.setId(1L);
        when(userRepository.findByToken("valid-token")).thenReturn(user);

        Message<?> result = interceptor.preSend(buildConnectMessage("valid-token"), channel);

        assertNotNull(result);
    }

    @Test
    void connect_missingToken_throwsMessagingException() {
        assertThrows(MessagingException.class,
                () -> interceptor.preSend(buildConnectMessage(null), channel));
    }

    @Test
    void connect_invalidToken_throwsMessagingException() {
        when(userRepository.findByToken("bad-token")).thenReturn(null);

        assertThrows(MessagingException.class,
                () -> interceptor.preSend(buildConnectMessage("bad-token"), channel));
    }
}
