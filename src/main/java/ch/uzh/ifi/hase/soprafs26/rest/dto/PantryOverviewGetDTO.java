package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.util.List;

public class PantryOverviewGetDTO {

    private List<PantryItemGetDTO> items;
    private Double totalCalories;

    public List<PantryItemGetDTO> getItems() {
        return items;
    }

    public void setItems(List<PantryItemGetDTO> items) {
        this.items = items;
    }

    public Double getTotalCalories() {
        return totalCalories;
    }

    public void setTotalCalories(Double totalCalories) {
        this.totalCalories = totalCalories;
    }
}