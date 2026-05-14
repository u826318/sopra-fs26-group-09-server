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

    @Column(nullable = true)
    private String productNameSnapshot;

    @Column(nullable = false)
    private Integer consumedQuantity;

    // Issue #133 — unit stored alongside quantity so activity feed can display "200g" instead of "200×"
    @Column(nullable = true)
    private String consumedUnit;

    @Column(nullable = true)
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

    public String getProductNameSnapshot() {
        return productNameSnapshot;
    }

    public void setProductNameSnapshot(String productNameSnapshot) {
        this.productNameSnapshot = productNameSnapshot;
    }

    public Integer getConsumedQuantity() {
        return consumedQuantity;
    }

    public void setConsumedQuantity(Integer consumedQuantity) {
        this.consumedQuantity = consumedQuantity;
    }

    public String getConsumedUnit() {
        return consumedUnit;
    }

    public void setConsumedUnit(String consumedUnit) {
        this.consumedUnit = consumedUnit;
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
