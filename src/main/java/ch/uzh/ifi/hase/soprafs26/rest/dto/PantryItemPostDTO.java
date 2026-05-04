package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.util.Map;

public class PantryItemPostDTO {

    private String barcode;
    private String name;
    private Double kcalPerPackage;
    private Integer quantity;
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

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

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
