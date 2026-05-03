package ch.uzh.ifi.hase.soprafs26.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "nutrient_reference")
public class NutrientReference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String standard;
    private String nutrientKey;
    private String displayName;
    private String category;

    private String sex;
    private String lifeStage;

    private Integer ageMinMonths;
    private Integer ageMaxMonths;

    /**
     * Keep the raw CSV value here.
     * This can be "1000", "3.5", or "ND".
     */
    private String targetValue;

    /**
     * Nullable numeric version.
     * For "ND", this stays null.
     */
    @Column(precision = 12, scale = 4)
    private BigDecimal targetValueNumeric;

    private String targetMinValue;
    private String targetMaxValue;

    private String unit;
    private String referenceType;

    private String source;

    @Column(length = 1000)
    private String notes;

    public Long getId() {
        return id;
    }

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

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getLifeStage() {
        return lifeStage;
    }

    public void setLifeStage(String lifeStage) {
        this.lifeStage = lifeStage;
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

    public String getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(String targetValue) {
        this.targetValue = targetValue;
    }

    public BigDecimal getTargetValueNumeric() {
        return targetValueNumeric;
    }

    public void setTargetValueNumeric(BigDecimal targetValueNumeric) {
        this.targetValueNumeric = targetValueNumeric;
    }

    public String getTargetMinValue() {
        return targetMinValue;
    }

    public void setTargetMinValue(String targetMinValue) {
        this.targetMinValue = targetMinValue;
    }

    public String getTargetMaxValue() {
        return targetMaxValue;
    }

    public void setTargetMaxValue(String targetMaxValue) {
        this.targetMaxValue = targetMaxValue;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}