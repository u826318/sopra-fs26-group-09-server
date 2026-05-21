package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.time.LocalDate;
import java.util.Map;

public class PantryItemPostDTO {

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
    private String packageQuantity;
    private Map<String, Object> nutriments;

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

    public LocalDate getExpirationDate() { return expirationDate; }
    public void setExpirationDate(LocalDate expirationDate) { this.expirationDate = expirationDate; }

    public String getPackageQuantity() {
        return packageQuantity;
    }

    public void setPackageQuantity(String packageQuantity) {
        this.packageQuantity = packageQuantity;
    }

    public Map<String, Object> getNutriments() {
        return nutriments;
    }

    public void setNutriments(Map<String, Object> nutriments) {
        this.nutriments = nutriments;
    }
}
