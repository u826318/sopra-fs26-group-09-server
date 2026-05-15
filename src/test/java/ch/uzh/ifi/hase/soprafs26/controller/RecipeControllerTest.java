package ch.uzh.ifi.hase.soprafs26.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ch.uzh.ifi.hase.soprafs26.config.AuthFilter;
import ch.uzh.ifi.hase.soprafs26.exceptions.GlobalExceptionAdvice;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.RecipeCookResponseDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.RecipeRecommendationGetDTO;
import ch.uzh.ifi.hase.soprafs26.service.RecipeService;

@WebMvcTest(RecipeController.class)
@Import(GlobalExceptionAdvice.class)
class RecipeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecipeService recipeService;

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
    void getRecipeRecommendations_success_returnsRecipes() throws Exception {
        RecipeRecommendationGetDTO recipe = new RecipeRecommendationGetDTO();
        recipe.setId("tomato-pasta-light");
        recipe.setTitle("Light Tomato Pasta");
        recipe.setReadyToCook(true);
        recipe.setMatchScore(92);
        recipe.setMissingIngredientCount(0);

        when(recipeService.getRecommendations(1L, 99L)).thenReturn(List.of(recipe));

        mockMvc.perform(get("/households/1/recipes/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("tomato-pasta-light"))
                .andExpect(jsonPath("$[0].title").value("Light Tomato Pasta"))
                .andExpect(jsonPath("$[0].readyToCook").value(true))
                .andExpect(jsonPath("$[0].matchScore").value(92));
    }

    @Test
    void cookRecipe_success_returnsCookResponse() throws Exception {
        RecipeCookResponseDTO response = new RecipeCookResponseDTO();
        response.setRecipeId("tomato-pasta-light");
        response.setTitle("Light Tomato Pasta");
        response.setServingsCooked(2);
        response.setConsumedCalories(300.0);

        when(recipeService.cookRecipe(1L, "tomato-pasta-light", 2, 99L)).thenReturn(response);

        mockMvc.perform(post("/households/1/recipes/tomato-pasta-light/cook")
                        .contentType("application/json")
                        .content("{\"servings\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipeId").value("tomato-pasta-light"))
                .andExpect(jsonPath("$.servingsCooked").value(2))
                .andExpect(jsonPath("$.consumedCalories").value(300.0));
    }

    @Test
    void cookRecipe_invalidRequest_returnsBadRequest() throws Exception {
        when(recipeService.cookRecipe(1L, "tomato-pasta-light", 0, 99L))
                .thenThrow(new IllegalArgumentException("Servings must be between 1 and 12."));

        mockMvc.perform(post("/households/1/recipes/tomato-pasta-light/cook")
                        .contentType("application/json")
                        .content("{\"servings\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Servings must be between 1 and 12."));
    }
}
