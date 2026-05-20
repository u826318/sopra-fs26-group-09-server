package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class ConsumePantryItemPostDTO {

    // Issue #133 — amount replaces quantity (Integer) to support partial/portion consumption
    private Double amount;
    private String amountUnit;
    private Double kcalPerPackage;
    private Boolean skipCalorieLogging;

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getAmountUnit() {
        return amountUnit;
    }

    public void setAmountUnit(String amountUnit) {
        this.amountUnit = amountUnit;
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

    // Issue #121 — optional; null means the authenticated user is the consumer
    private Long consumedForUserId;

    public Long getConsumedForUserId() {
        return consumedForUserId;
    }

    public void setConsumedForUserId(Long consumedForUserId) {
        this.consumedForUserId = consumedForUserId;
    }
}
