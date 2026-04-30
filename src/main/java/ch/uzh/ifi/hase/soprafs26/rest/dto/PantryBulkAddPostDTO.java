package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.util.List;

public class PantryBulkAddPostDTO {

    private List<PantryItemPostDTO> items;

    public List<PantryItemPostDTO> getItems() {
        return items;
    }

    public void setItems(List<PantryItemPostDTO> items) {
        this.items = items;
    }
}
