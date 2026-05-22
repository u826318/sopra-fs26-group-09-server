package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.time.LocalDate;
import java.util.Map;

public class PantryItemPostDTO {

    private Long productIndex;
    private String barcode;
    private String name;
    private LocalDate expirationDate;
    private Double kcalPerPackage;
    // Issue #114 — amount in the unit chosen by the user
    private Double amount;

    // one of: "g", "ml", "package"
    private String amountUnit;

    // populated when amountUnit = "g"
    private Double kcalPer100g;

    // populated when amountUnit = "ml"
    private Double kcalPer100ml;
    // populated when amountUnit = "serving"
    private Double kcalPerServing;
    // Package metadata is required for local-dataset products when the dataset does not provide it.
    // It is stored as conversion metadata so pantry amount can stay as fractional packages.
    private String packageQuantity;
    private String packageQuantityUnit;
    private Map<String, Object> nutriments;
    private Boolean manualEntry;
    private Map<String, PantryItemMicronutrientPostDTO> micronutrients;

    public Long getProductIndex() {
        return productIndex;
    }

    public void setProductIndex(Long productIndex) {
        this.productIndex = productIndex;
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

    public String getAmountUnit() { return amountUnit; }
    public void setAmountUnit(String amountUnit) { this.amountUnit = amountUnit; }

    public Double getKcalPer100g() { return kcalPer100g; }
    public void setKcalPer100g(Double kcalPer100g) { this.kcalPer100g = kcalPer100g; }

    public Double getKcalPer100ml() { return kcalPer100ml; }
    public void setKcalPer100ml(Double kcalPer100ml) { this.kcalPer100ml = kcalPer100ml; }

    public Double getKcalPerServing() { return kcalPerServing; }
    public void setKcalPerServing(Double kcalPerServing) { this.kcalPerServing = kcalPerServing; }

    public LocalDate getExpirationDate() { return expirationDate; }
    public void setExpirationDate(LocalDate expirationDate) { this.expirationDate = expirationDate; }

    public String getPackageQuantity() {
        return packageQuantity;
    }

    public void setPackageQuantity(String packageQuantity) {
        this.packageQuantity = packageQuantity;
    }

    public String getPackageQuantityUnit() {
        return packageQuantityUnit;
    }

    public void setPackageQuantityUnit(String packageQuantityUnit) {
        this.packageQuantityUnit = packageQuantityUnit;
    }

    public Map<String, Object> getNutriments() {
        return nutriments;
    }

    public void setNutriments(Map<String, Object> nutriments) {
        this.nutriments = nutriments;
    }

    public Boolean getManualEntry() {
        return manualEntry;
    }

    public void setManualEntry(Boolean manualEntry) {
        this.manualEntry = manualEntry;
    }

    public Map<String, PantryItemMicronutrientPostDTO> getMicronutrients() {
        return micronutrients;
    }

    public void setMicronutrients(Map<String, PantryItemMicronutrientPostDTO> micronutrients) {
        this.micronutrients = micronutrients;
    }
}

