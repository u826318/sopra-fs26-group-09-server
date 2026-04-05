package ch.uzh.ifi.hase.soprafs26.entity;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "consumption_logs")
public class ConsumptionLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private Long householdId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long pantryItemId;

    @Column(nullable = false)
    private Integer consumedQuantity;

    @Column(nullable = false)
    private Double consumedCalories;

    @Column(nullable = false, updatable = false)
    private Instant consumedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getHouseholdId() {
        return householdId;
    }

    public void setHouseholdId(Long householdId) {
        this.householdId = householdId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getPantryItemId() {
        return pantryItemId;
    }

    public void setPantryItemId(Long pantryItemId) {
        this.pantryItemId = pantryItemId;
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

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }
}