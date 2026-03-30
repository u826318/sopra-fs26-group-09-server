package ch.uzh.ifi.hase.soprafs26.config;

import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthFilterTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FilterChain filterChain;

    private AuthFilter authFilter;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        authFilter = new AuthFilter(userRepository);
    }

    @Test
    void validToken_setsAttributeAndContinues() throws Exception {
        User user = new User();
        user.setToken("valid-token");
        when(userRepository.findByToken("valid-token")).thenReturn(user);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        authFilter.doFilterInternal(request, response, filterChain);

        assertEquals(user, request.getAttribute("authenticatedUser"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void invalidToken_returns401() throws Exception {
        when(userRepository.findByToken("bad-token")).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        authFilter.doFilterInternal(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(filterChain, never()).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    void missingToken_returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        authFilter.doFilterInternal(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(filterChain, never()).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    void emptyToken_returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        authFilter.doFilterInternal(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(filterChain, never()).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    void registerPath_skipsFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/users/register");

        assertTrue(authFilter.shouldNotFilter(request));
    }

    @Test
    void loginPath_skipsFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/users/login");

        assertTrue(authFilter.shouldNotFilter(request));
    }

    @Test
    void otherPath_doesNotSkipFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/households");

        assertFalse(authFilter.shouldNotFilter(request));
    }
}
