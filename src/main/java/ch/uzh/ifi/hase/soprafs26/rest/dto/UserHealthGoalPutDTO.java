package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class UserHealthGoalPutDTO {

    private String goalType;
    // targetRate removed — backend derives it from targetWeight and weeksToGoal
    private Double targetWeight;  // kg; required when goalType == LOSE_WEIGHT
    private Integer weeksToGoal;  // required when goalType == LOSE_WEIGHT
    private Integer age;
    private String sex;
    private Double height;
    private Double weight;
    private String activityLevel;

    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }

    public Double getTargetWeight() { return targetWeight; }
    public void setTargetWeight(Double targetWeight) { this.targetWeight = targetWeight; }

    public Integer getWeeksToGoal() { return weeksToGoal; }
    public void setWeeksToGoal(Integer weeksToGoal) { this.weeksToGoal = weeksToGoal; }

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
}
