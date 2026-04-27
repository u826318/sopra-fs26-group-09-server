package ch.uzh.ifi.hase.soprafs26.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "user_health_goals")
public class UserHealthGoal implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private Long goalId;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private String goalType;

    @Column
    private Double targetRate;

    @Column(nullable = false)
    private Integer age;

    @Column(nullable = false)
    private String sex;

    @Column(nullable = false)
    private Double height;

    @Column(nullable = false)
    private Double weight;

    @Column(nullable = false)
    private String activityLevel;

    @Column(nullable = false)
    private Double recommendedDailyCalories;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    private void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getGoalId() { return goalId; }
    public void setGoalId(Long goalId) { this.goalId = goalId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }

    public Double getTargetRate() { return targetRate; }
    public void setTargetRate(Double targetRate) { this.targetRate = targetRate; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getSex() { return sex; }
    public void setSex(String sex) { this.sex = sex; }

    public Double getHeight() { return height; }
    public void setHeight(Double height) { this.height = height; }

    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }

    public String getActivityLevel() { return activityLevel; }
    public void setActivityLevel(String activityLevel) { this.activityLevel = activityLevel; }

    public Double getRecommendedDailyCalories() { return recommendedDailyCalories; }
    public void setRecommendedDailyCalories(Double v) { this.recommendedDailyCalories = v; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
