package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.util.List;

public class HouseholdStatsGetDTO {

    private String startDate;
    private String endDate;
    private Double dailyCalorieTarget;
    private Double averageDailyCalories;
    private Double totalCaloriesConsumed;
    private List<DailyBreakdownDTO> dailyBreakdown;
    private ComparisonToBudgetDTO comparisonToBudget;

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public Double getDailyCalorieTarget() {
        return dailyCalorieTarget;
    }

    public void setDailyCalorieTarget(Double dailyCalorieTarget) {
        this.dailyCalorieTarget = dailyCalorieTarget;
    }

    public Double getAverageDailyCalories() {
        return averageDailyCalories;
    }

    public void setAverageDailyCalories(Double averageDailyCalories) {
        this.averageDailyCalories = averageDailyCalories;
    }

    public Double getTotalCaloriesConsumed() {
        return totalCaloriesConsumed;
    }

    public void setTotalCaloriesConsumed(Double totalCaloriesConsumed) {
        this.totalCaloriesConsumed = totalCaloriesConsumed;
    }

    public List<DailyBreakdownDTO> getDailyBreakdown() {
        return dailyBreakdown;
    }

    public void setDailyBreakdown(List<DailyBreakdownDTO> dailyBreakdown) {
        this.dailyBreakdown = dailyBreakdown;
    }

    public ComparisonToBudgetDTO getComparisonToBudget() {
        return comparisonToBudget;
    }

    public void setComparisonToBudget(ComparisonToBudgetDTO comparisonToBudget) {
        this.comparisonToBudget = comparisonToBudget;
    }

    public static class DailyBreakdownDTO {
        private String date;
        private Double caloriesConsumed;

        public DailyBreakdownDTO(String date, Double caloriesConsumed) {
            this.date = date;
            this.caloriesConsumed = caloriesConsumed;
        }

        public String getDate() {
            return date;
        }

        public Double getCaloriesConsumed() {
            return caloriesConsumed;
        }
    }

    public static class ComparisonToBudgetDTO {
        private String status;
        private Double differenceFromTarget;
        private Double percentageOfTarget;

        public ComparisonToBudgetDTO(String status, Double differenceFromTarget, Double percentageOfTarget) {
            this.status = status;
            this.differenceFromTarget = differenceFromTarget;
            this.percentageOfTarget = percentageOfTarget;
        }

        public String getStatus() {
            return status;
        }

        public Double getDifferenceFromTarget() {
            return differenceFromTarget;
        }

        public Double getPercentageOfTarget() {
            return percentageOfTarget;
        }
    }
}
