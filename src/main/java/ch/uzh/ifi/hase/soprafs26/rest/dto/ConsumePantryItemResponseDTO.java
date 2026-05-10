package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class ConsumePantryItemResponseDTO {

    private Long itemId;
    // Issue #133 — remainingAmount (Double) replaces remainingCount (Integer)
    private Double remainingAmount;
    private Double consumedCalories;
    private boolean removed;

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Double getRemainingAmount() {
        return remainingAmount;
    }

    public void setRemainingAmount(Double remainingAmount) {
        this.remainingAmount = remainingAmount;
    }

    public Double getConsumedCalories() {
        return consumedCalories;
    }

    public void setConsumedCalories(Double consumedCalories) {
        this.consumedCalories = consumedCalories;
    }

    public boolean isRemoved() {
        return removed;
    }

    public void setRemoved(boolean removed) {
        this.removed = removed;
    }
}