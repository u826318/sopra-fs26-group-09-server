package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class PantryItemMicronutrientPostDTO {

    private Double value;
    private String unit;

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }
}
