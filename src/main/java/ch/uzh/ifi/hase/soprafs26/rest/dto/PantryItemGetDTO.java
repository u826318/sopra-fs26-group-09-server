package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.time.Instant;
import java.util.List;

public class PantryItemGetDTO {

    private Long id;
    private Long householdId;
    private String barcode;
    private String name;
    private Double kcalPerPackage;
    // Issue #114 — remaining amount in the stored unit
    private Double amount;
    // original quantity at the time of first add — never modified by consumption
    private Double initialAmount;
    // one of: "g", "ml", "package"
    private String amountUnit;
    // populated when amountUnit = "g"
    private Double kcalPer100g;
    // populated when amountUnit = "ml"
    private Double kcalPer100ml;

    // Local dataset nutrition/conversion metadata. Nutrient values are stored per nutrition basis,
    // usually per 100g or per 100ml. Package/serving metadata is optional and controls consume options.
    private Double nutritionBasisAmount;
    private String nutritionBasisUnit;
    private Double packageQuantity;
    private String packageQuantityUnit;
    private Double servingQuantity;
    private String servingQuantityUnit;
    private List<String> availableConsumptionUnits;

    private Instant addedAt;

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

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getKcalPerPackage() {
        return kcalPerPackage;
    }

    public void setKcalPerPackage(Double kcalPerPackage) {
        this.kcalPerPackage = kcalPerPackage;
    }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public Double getInitialAmount() { return initialAmount; }
    public void setInitialAmount(Double initialAmount) { this.initialAmount = initialAmount; }

    public String getAmountUnit() { return amountUnit; }
    public void setAmountUnit(String amountUnit) { this.amountUnit = amountUnit; }

    public Double getKcalPer100g() { return kcalPer100g; }
    public void setKcalPer100g(Double kcalPer100g) { this.kcalPer100g = kcalPer100g; }

    public Double getKcalPer100ml() { return kcalPer100ml; }
    public void setKcalPer100ml(Double kcalPer100ml) { this.kcalPer100ml = kcalPer100ml; }

    public Double getNutritionBasisAmount() { return nutritionBasisAmount; }
    public void setNutritionBasisAmount(Double nutritionBasisAmount) { this.nutritionBasisAmount = nutritionBasisAmount; }

    public String getNutritionBasisUnit() { return nutritionBasisUnit; }
    public void setNutritionBasisUnit(String nutritionBasisUnit) { this.nutritionBasisUnit = nutritionBasisUnit; }

    public Double getPackageQuantity() { return packageQuantity; }
    public void setPackageQuantity(Double packageQuantity) { this.packageQuantity = packageQuantity; }

    public String getPackageQuantityUnit() { return packageQuantityUnit; }
    public void setPackageQuantityUnit(String packageQuantityUnit) { this.packageQuantityUnit = packageQuantityUnit; }

    public Double getServingQuantity() { return servingQuantity; }
    public void setServingQuantity(Double servingQuantity) { this.servingQuantity = servingQuantity; }

    public String getServingQuantityUnit() { return servingQuantityUnit; }
    public void setServingQuantityUnit(String servingQuantityUnit) { this.servingQuantityUnit = servingQuantityUnit; }

    public List<String> getAvailableConsumptionUnits() { return availableConsumptionUnits; }
    public void setAvailableConsumptionUnits(List<String> availableConsumptionUnits) { this.availableConsumptionUnits = availableConsumptionUnits; }

    public Instant getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(Instant addedAt) {
        this.addedAt = addedAt;
    }
}