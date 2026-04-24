package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.time.Instant;

public class ConsumptionLogGetDTO {

    private Long logId;
    private Instant consumedAt;
    private Long pantryItemId;
    private String productName;
    private Integer consumedQuantity;
    private Double consumedCalories;
    private Long userId;
    private String username;

    public Long getLogId() {
        return logId;
    }

    public void setLogId(Long logId) {
        this.logId = logId;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }

    public Long getPantryItemId() {
        return pantryItemId;
    }

    public void setPantryItemId(Long pantryItemId) {
        this.pantryItemId = pantryItemId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Integer getConsumedQuantity() {
        return consumedQuantity;
    }

    public void setConsumedQuantity(Integer consumedQuantity) {
        this.consumedQuantity = consumedQuantity;
    }

    public Double getConsumedCalories() {
        return consumedCalories;
    }

    public void setConsumedCalories(Double consumedCalories) {
        this.consumedCalories = consumedCalories;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
