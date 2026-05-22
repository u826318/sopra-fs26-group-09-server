package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.util.List;

public class RecipeRecommendationGetDTO {
    private String id;
    private String title;
    private String summary;
    private String imageEmoji;
    private String source;
    private Integer servings;
    private boolean readyToCook;
    private Integer matchScore;
    private Integer matchedIngredientCount;
    private Integer missingIngredientCount;
    private String healthGoalFit;
    private String recommendationReason;
    private Double caloriesPerServing;
    private Double proteinGrams;
    private Double carbsGrams;
    private Double fatGrams;
    private List<String> tags;
    private List<String> instructions;
    private List<RecipeIngredientGetDTO> ingredients;
    private List<String> missingIngredients;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getImageEmoji() {
        return imageEmoji;
    }

    public void setImageEmoji(String imageEmoji) {
        this.imageEmoji = imageEmoji;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Integer getServings() {
        return servings;
    }

    public void setServings(Integer servings) {
        this.servings = servings;
    }

    public boolean isReadyToCook() {
        return readyToCook;
    }

    public void setReadyToCook(boolean readyToCook) {
        this.readyToCook = readyToCook;
    }

    public Integer getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(Integer matchScore) {
        this.matchScore = matchScore;
    }

    public Integer getMatchedIngredientCount() {
        return matchedIngredientCount;
    }

    public void setMatchedIngredientCount(Integer matchedIngredientCount) {
        this.matchedIngredientCount = matchedIngredientCount;
    }

    public Integer getMissingIngredientCount() {
        return missingIngredientCount;
    }

    public void setMissingIngredientCount(Integer missingIngredientCount) {
        this.missingIngredientCount = missingIngredientCount;
    }

    public String getHealthGoalFit() {
        return healthGoalFit;
    }

    public void setHealthGoalFit(String healthGoalFit) {
        this.healthGoalFit = healthGoalFit;
    }

    public String getRecommendationReason() {
        return recommendationReason;
    }

    public void setRecommendationReason(String recommendationReason) {
        this.recommendationReason = recommendationReason;
    }

    public Double getCaloriesPerServing() {
        return caloriesPerServing;
    }

    public void setCaloriesPerServing(Double caloriesPerServing) {
        this.caloriesPerServing = caloriesPerServing;
    }

    public Double getProteinGrams() {
        return proteinGrams;
    }

    public void setProteinGrams(Double proteinGrams) {
        this.proteinGrams = proteinGrams;
    }

    public Double getCarbsGrams() {
        return carbsGrams;
    }

    public void setCarbsGrams(Double carbsGrams) {
        this.carbsGrams = carbsGrams;
    }

    public Double getFatGrams() {
        return fatGrams;
    }

    public void setFatGrams(Double fatGrams) {
        this.fatGrams = fatGrams;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<String> getInstructions() {
        return instructions;
    }

    public void setInstructions(List<String> instructions) {
        this.instructions = instructions;
    }

    public List<RecipeIngredientGetDTO> getIngredients() {
        return ingredients;
    }

    public void setIngredients(List<RecipeIngredientGetDTO> ingredients) {
        this.ingredients = ingredients;
    }

    public List<String> getMissingIngredients() {
        return missingIngredients;
    }

    public void setMissingIngredients(List<String> missingIngredients) {
        this.missingIngredients = missingIngredients;
    }
}
