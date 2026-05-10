package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class ConsumePantryItemPostDTO {

    // Issue #133 — amount replaces quantity (Integer) to support partial/portion consumption
    private Double amount;
    private Double kcalPerPackage;
    private Boolean skipCalorieLogging;

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
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
