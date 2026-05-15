package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class RecipeIngredientGetDTO {
    private String name;
    private Double amount;
    private String unit;
    private boolean matched;
    private Long pantryItemId;
    private String pantryItemName;
    private Double availableAmount;
    private String pantryUnit;
    private boolean enoughAvailable;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }

    public Long getPantryItemId() {
        return pantryItemId;
    }

    public void setPantryItemId(Long pantryItemId) {
        this.pantryItemId = pantryItemId;
    }

    public String getPantryItemName() {
        return pantryItemName;
    }

    public void setPantryItemName(String pantryItemName) {
        this.pantryItemName = pantryItemName;
    }

    public Double getAvailableAmount() {
        return availableAmount;
    }

    public void setAvailableAmount(Double availableAmount) {
        this.availableAmount = availableAmount;
    }

    public String getPantryUnit() {
        return pantryUnit;
    }

    public void setPantryUnit(String pantryUnit) {
        this.pantryUnit = pantryUnit;
    }

    public boolean isEnoughAvailable() {
        return enoughAvailable;
    }

    public void setEnoughAvailable(boolean enoughAvailable) {
        this.enoughAvailable = enoughAvailable;
    }
}
