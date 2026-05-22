package ch.uzh.ifi.hase.soprafs26.exceptions;

import ch.uzh.ifi.hase.soprafs26.rest.dto.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionAdviceTest {

    private GlobalExceptionAdvice advice;

    @BeforeEach
    void setUp() {
        advice = new GlobalExceptionAdvice();
    }

    // -----------------------------------------------------------------------
    // handleBadRequest — IllegalArgumentException → 400
    // -----------------------------------------------------------------------

    @Test
    void handleBadRequest_illegalArgumentException_returns400WithMessage() {
        IllegalArgumentException ex = new IllegalArgumentException("bad argument");
        WebRequest request = mock(WebRequest.class);
        // WebRequest.getDescription is called by Spring's handleExceptionInternal
        when(request.getDescription(false)).thenReturn("uri=/test");

        ResponseEntity<Object> response = advice.handleBadRequest(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertInstanceOf(ErrorResponseDTO.class, response.getBody());
        ErrorResponseDTO body = (ErrorResponseDTO) response.getBody();
        assertEquals("bad argument", body.getMessage());
    }

    @Test
    void handleBadRequest_illegalStateException_returns400() {
        IllegalStateException ex = new IllegalStateException("bad state");
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=/test");

        ResponseEntity<Object> response = advice.handleBadRequest(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        ErrorResponseDTO body = (ErrorResponseDTO) response.getBody();
        assertEquals("bad state", body.getMessage());
    }

    // -----------------------------------------------------------------------
    // handleTransactionSystemException — → 409 CONFLICT
    // -----------------------------------------------------------------------

    @Test
    void handleTransactionSystemException_returns409() {
        Exception cause = new RuntimeException("constraint violation");
        TransactionSystemException ex = new TransactionSystemException("tx failed", cause);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost/api/test"));

        ResponseStatusException result = advice.handleTransactionSystemException(ex, request);

        assertEquals(HttpStatus.CONFLICT, result.getStatusCode());
        assertTrue(result.getMessage().contains("tx failed"));
    }

    // -----------------------------------------------------------------------
    // handleException — InternalServerError → 500
    // -----------------------------------------------------------------------

    @Test
    void handleException_internalServerError_returns500() {
        HttpServerErrorException.InternalServerError ex =
                (HttpServerErrorException.InternalServerError)
                        HttpServerErrorException.create(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Internal Server Error",
                                null, null, null);

        ResponseStatusException result = advice.handleException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
    }
}
