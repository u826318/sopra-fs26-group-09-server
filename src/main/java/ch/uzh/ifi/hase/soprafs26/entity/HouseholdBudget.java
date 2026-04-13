package ch.uzh.ifi.hase.soprafs26.entity;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "household_budgets")
public class HouseholdBudget implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false, unique = true)
    private Long householdId;

    @Column(nullable = false)
    private Double dailyCalorieTarget;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    private void preUpdate() {
        this.updatedAt = Instant.now();
    }

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

    public Double getDailyCalorieTarget() {
        return dailyCalorieTarget;
    }

    public void setDailyCalorieTarget(Double dailyCalorieTarget) {
        this.dailyCalorieTarget = dailyCalorieTarget;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
