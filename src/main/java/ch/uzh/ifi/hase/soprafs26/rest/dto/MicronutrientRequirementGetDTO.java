package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.math.BigDecimal;

import ch.uzh.ifi.hase.soprafs26.entity.LifeStageGroup;

public class MicronutrientRequirementGetDTO {

    private String standard;
    private String nutrientKey;
    private String displayName;
    private String category;
    private LifeStageGroup lifeStageGroup;
    private Integer ageMinMonths;
    private Integer ageMaxMonths;
    private String unit;
    private BigDecimal recommendedValue;
    private String recommendedReferenceType;
    private BigDecimal rdaValue;
    private BigDecimal aiValue;
    private BigDecimal upperLimitValue;
    private String sourceFiles;

    public String getStandard() {
        return standard;
    }

    public void setStandard(String standard) {
        this.standard = standard;
    }

    public String getNutrientKey() {
        return nutrientKey;
    }

    public void setNutrientKey(String nutrientKey) {
        this.nutrientKey = nutrientKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public LifeStageGroup getLifeStageGroup() {
        return lifeStageGroup;
    }

    public void setLifeStageGroup(LifeStageGroup lifeStageGroup) {
        this.lifeStageGroup = lifeStageGroup;
    }

    public Integer getAgeMinMonths() {
        return ageMinMonths;
    }

    public void setAgeMinMonths(Integer ageMinMonths) {
        this.ageMinMonths = ageMinMonths;
    }

    public Integer getAgeMaxMonths() {
        return ageMaxMonths;
    }

    public void setAgeMaxMonths(Integer ageMaxMonths) {
        this.ageMaxMonths = ageMaxMonths;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getRecommendedValue() {
        return recommendedValue;
    }

    public void setRecommendedValue(BigDecimal recommendedValue) {
        this.recommendedValue = recommendedValue;
    }

    public String getRecommendedReferenceType() {
        return recommendedReferenceType;
    }

    public void setRecommendedReferenceType(String recommendedReferenceType) {
        this.recommendedReferenceType = recommendedReferenceType;
    }

    public BigDecimal getRdaValue() {
        return rdaValue;
    }

    public void setRdaValue(BigDecimal rdaValue) {
        this.rdaValue = rdaValue;
    }

    public BigDecimal getAiValue() {
        return aiValue;
    }

    public void setAiValue(BigDecimal aiValue) {
        this.aiValue = aiValue;
    }

    public BigDecimal getUpperLimitValue() {
        return upperLimitValue;
    }

    public void setUpperLimitValue(BigDecimal upperLimitValue) {
        this.upperLimitValue = upperLimitValue;
    }

    public String getSourceFiles() {
        return sourceFiles;
    }

    public void setSourceFiles(String sourceFiles) {
        this.sourceFiles = sourceFiles;
    }
}
