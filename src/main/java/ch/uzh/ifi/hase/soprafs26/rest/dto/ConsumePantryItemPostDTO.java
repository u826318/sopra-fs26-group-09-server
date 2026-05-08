package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class ConsumePantryItemPostDTO {

    private Integer quantity;
    private Double kcalPerPackage;
    private Boolean skipCalorieLogging;

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Double getKcalPerPackage() {
        return kcalPerPackage;
    }

    public void setKcalPerPackage(Double kcalPerPackage) {
        this.kcalPerPackage = kcalPerPackage;
    }

    public Boolean getSkipCalorieLogging() {
        return skipCalorieLogging;
    }

    public void setSkipCalorieLogging(Boolean skipCalorieLogging) {
        this.skipCalorieLogging = skipCalorieLogging;
    }
}
