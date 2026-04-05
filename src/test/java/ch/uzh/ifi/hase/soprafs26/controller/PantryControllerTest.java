package ch.uzh.ifi.hase.soprafs26.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.uzh.ifi.hase.soprafs26.config.AuthFilter;
import ch.uzh.ifi.hase.soprafs26.exceptions.GlobalExceptionAdvice;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.service.PantryService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

@WebMvcTest(PantryController.class)
@Import(GlobalExceptionAdvice.class)
class PantryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PantryService pantryService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AuthFilter authFilter;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);

            ((HttpServletRequest) request).setAttribute("authenticatedUserId", 99L);
            chain.doFilter(request, response);
            return null;
        }).when(authFilter).doFilter(any(), any(), any());
    }

    @Test
    void consumePantryItem_success_returnsOk() throws Exception {
        PantryService.ConsumeResult result = new PantryService.ConsumeResult();
        result.setItemId(10L);
        result.setRemainingCount(3);
        result.setConsumedCalories(200.0);
        result.setRemoved(false);

        when(pantryService.consumeItem(1L, 10L, 2, 99L)).thenReturn(result);

        String requestBody = """
                {
                  "quantity": 2
                }
                """;

        mockMvc.perform(post("/households/1/pantry/10/consume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemId").value(10))
                .andExpect(jsonPath("$.remainingCount").value(3))
                .andExpect(jsonPath("$.consumedCalories").value(200.0))
                .andExpect(jsonPath("$.removed").value(false));
    }

    @Test
    void consumePantryItem_invalidQuantity_returnsBadRequest() throws Exception {
        when(pantryService.consumeItem(1L, 10L, 0, 99L))
                .thenThrow(new IllegalArgumentException("Quantity must be greater than zero."));

        String requestBody = """
                {
                  "quantity": 0
                }
                """;

        mockMvc.perform(post("/households/1/pantry/10/consume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Quantity must be greater than zero."));
    }
}