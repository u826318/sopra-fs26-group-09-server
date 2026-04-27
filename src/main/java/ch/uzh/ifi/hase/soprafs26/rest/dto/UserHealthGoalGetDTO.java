package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.time.Instant;

public class UserHealthGoalGetDTO {

    private Long goalId;
    private Long userId;
    private String goalType;
    private Double targetRate;
    private Integer age;
    private String sex;
    private Double height;
    private Double weight;
    private String activityLevel;
    private Double recommendedDailyCalories;
    private Instant updatedAt;

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
