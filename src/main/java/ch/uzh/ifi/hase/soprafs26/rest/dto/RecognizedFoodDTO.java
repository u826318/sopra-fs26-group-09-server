package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class RecognizedFoodDTO {

    private String name;
    private Double kcalPer100g;
    private Double kcalPerServing;
    private Double suggestedAmount;
    private String unit;
    private Double confidence;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getKcalPer100g() {
        return kcalPer100g;
    }

    public void setKcalPer100g(Double kcalPer100g) {
        this.kcalPer100g = kcalPer100g;
    }

    public Double getKcalPerServing() {
        return kcalPerServing;
    }

    public void setKcalPerServing(Double kcalPerServing) {
        this.kcalPerServing = kcalPerServing;
    }

    public Double getSuggestedAmount() {
        return suggestedAmount;
    }

    public void setSuggestedAmount(Double suggestedAmount) {
        this.suggestedAmount = suggestedAmount;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }
}
