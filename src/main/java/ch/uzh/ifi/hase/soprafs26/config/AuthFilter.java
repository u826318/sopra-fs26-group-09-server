package ch.uzh.ifi.hase.soprafs26.config;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuthFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public AuthFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/users/register") || path.equals("/users/login");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = request.getHeader("Authorization");

        if (token == null || token.trim().isEmpty()) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Missing authorization token.");
            return;
        }

        User user = userRepository.findByToken(token);
        if (user == null) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid or expired token.");
            return;
        }

        request.setAttribute("authenticatedUser", user);
        filterChain.doFilter(request, response);
    }
}
