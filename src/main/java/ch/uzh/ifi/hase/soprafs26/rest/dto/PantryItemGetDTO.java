package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.time.Instant;

public class PantryItemGetDTO {

    private Long id;
    private Long householdId;
    private String barcode;
    private String name;
    private Double kcalPerPackage;
    // Issue #114 — remaining amount in the stored unit
    private Double amount;
    // one of: "g", "ml", "package"
    private String amountUnit;
    // populated when amountUnit = "g"
    private Double kcalPer100g;
    // populated when amountUnit = "ml"
    private Double kcalPer100ml;
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

    public String getAmountUnit() { return amountUnit; }
    public void setAmountUnit(String amountUnit) { this.amountUnit = amountUnit; }

    public Double getKcalPer100g() { return kcalPer100g; }
    public void setKcalPer100g(Double kcalPer100g) { this.kcalPer100g = kcalPer100g; }

    public Double getKcalPer100ml() { return kcalPer100ml; }
    public void setKcalPer100ml(Double kcalPer100ml) { this.kcalPer100ml = kcalPer100ml; }

    public Instant getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(Instant addedAt) {
        this.addedAt = addedAt;
    }
}