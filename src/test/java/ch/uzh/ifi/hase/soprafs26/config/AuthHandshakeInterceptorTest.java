package ch.uzh.ifi.hase.soprafs26.config;

import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuthHandshakeInterceptorTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ServerHttpRequest request;

    @Mock
    private ServerHttpResponse response;

    @Mock
    private WebSocketHandler wsHandler;

    private AuthHandshakeInterceptor interceptor;
    private Map<String, Object> attributes;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        interceptor = new AuthHandshakeInterceptor(userRepository);
        attributes = new HashMap<>();
    }

    @Test
    void beforeHandshake_validToken_returnsTrue() throws Exception {
        User user = new User();
        user.setToken("valid-token");
        Mockito.when(request.getURI()).thenReturn(new URI("/ws?token=valid-token"));
        Mockito.when(userRepository.findByToken("valid-token")).thenReturn(user);

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertTrue(result);
        assertEquals(user, attributes.get("user"));
    }

    @Test
    void beforeHandshake_invalidToken_returnsFalse() throws Exception {
        Mockito.when(request.getURI()).thenReturn(new URI("/ws?token=invalid-token"));
        Mockito.when(userRepository.findByToken("invalid-token")).thenReturn(null);

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertFalse(result);
    }

    @Test
    void beforeHandshake_missingToken_returnsFalse() throws Exception {
        Mockito.when(request.getURI()).thenReturn(new URI("/ws"));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertFalse(result);
    }

    @Test
    void beforeHandshake_emptyToken_returnsFalse() throws Exception {
        Mockito.when(request.getURI()).thenReturn(new URI("/ws?token="));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertFalse(result);
    }
}
