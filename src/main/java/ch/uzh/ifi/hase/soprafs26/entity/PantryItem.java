package ch.uzh.ifi.hase.soprafs26.entity;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "pantry_items")
public class PantryItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private Long householdId;

    @Column
    private String barcode;

    @Column(nullable = false)
    private String name;

    // Issue #114 — store tracking unit chosen by user at add time
    // amountUnit is one of: "g", "ml", "package"
    @Column(nullable = false)
    private String amountUnit;

    // amount in the chosen unit (Double allows partial consumption)
    @Column(nullable = false)
    private Double amount;

    // original quantity at the time of first add — never modified by consumption
    @Column(nullable = false, updatable = false)
    private Double initialAmount;

    // kcalPerPackage: used when amountUnit = "package"
    @Column
    private Double kcalPerPackage;

    // kcalPer100g: used when amountUnit = "g"
    @Column
    private Double kcalPer100g;

    // kcalPer100ml: used when amountUnit = "ml"
    @Column
    private Double kcalPer100ml;

    @Column(nullable = false, updatable = false)
    private Instant addedAt;

    @OneToOne(mappedBy = "pantryItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private PantryItemMicronutrients micronutrients;

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

    public String getAmountUnit() { return amountUnit; }
    public void setAmountUnit(String amountUnit) { this.amountUnit = amountUnit; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public Double getInitialAmount() { return initialAmount; }
    public void setInitialAmount(Double initialAmount) { this.initialAmount = initialAmount; }

    public Double getKcalPerPackage() {
        return kcalPerPackage;
    }

    public void setKcalPerPackage(Double kcalPerPackage) {
        this.kcalPerPackage = kcalPerPackage;
    }

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

    public PantryItemMicronutrients getMicronutrients() {
        return micronutrients;
    }

    public void setMicronutrients(PantryItemMicronutrients micronutrients) {
        this.micronutrients = micronutrients;
    }
}