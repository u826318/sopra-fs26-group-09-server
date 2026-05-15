package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.util.List;

public class RecipeCookResponseDTO {
    private String recipeId;
    private String title;
    private Integer servingsCooked;
    private Double consumedCalories;
    private List<RecipeIngredientGetDTO> consumedIngredients;

    public String getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(String recipeId) {
        this.recipeId = recipeId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getServingsCooked() {
        return servingsCooked;
    }

    public void setServingsCooked(Integer servingsCooked) {
        this.servingsCooked = servingsCooked;
    }

    public Double getConsumedCalories() {
        return consumedCalories;
    }

    public void setConsumedCalories(Double consumedCalories) {
        this.consumedCalories = consumedCalories;
    }

    public List<RecipeIngredientGetDTO> getConsumedIngredients() {
        return consumedIngredients;
    }

    public void setConsumedIngredients(List<RecipeIngredientGetDTO> consumedIngredients) {
        this.consumedIngredients = consumedIngredients;
    }
}
