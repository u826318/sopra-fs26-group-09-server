package ch.uzh.ifi.hase.soprafs26.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ch.uzh.ifi.hase.soprafs26.rest.dto.RecipeCookPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.RecipeCookResponseDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.RecipeRecommendationGetDTO;
import ch.uzh.ifi.hase.soprafs26.service.RecipeService;

@RestController
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @GetMapping("/households/{householdId}/recipes/recommendations")
    @ResponseStatus(HttpStatus.OK)
    public List<RecipeRecommendationGetDTO> getRecipeRecommendations(
            @RequestAttribute("authenticatedUserId") Long authenticatedUserId,
            @PathVariable Long householdId) {
        return recipeService.getRecommendations(householdId, authenticatedUserId);
    }

    @PostMapping("/households/{householdId}/recipes/{recipeId}/cook")
    @ResponseStatus(HttpStatus.OK)
    public RecipeCookResponseDTO cookRecipe(
            @RequestAttribute("authenticatedUserId") Long authenticatedUserId,
            @PathVariable Long householdId,
            @PathVariable String recipeId,
            @RequestBody(required = false) RecipeCookPostDTO body) {
        Integer servings = body == null ? null : body.getServings();
        return recipeService.cookRecipe(householdId, recipeId, servings, authenticatedUserId);
    }
}
