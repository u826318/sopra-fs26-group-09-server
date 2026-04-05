package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class ConsumePantryItemResponseDTO {

    private Long itemId;
    private Integer remainingCount;
    private Double consumedCalories;
    private boolean removed;

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Integer getRemainingCount() {
        return remainingCount;
    }

    public void setRemainingCount(Integer remainingCount) {
        this.remainingCount = remainingCount;
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