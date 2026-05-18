package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class ConsumePantryItemPostDTO {

    private Integer quantity;

    // Compatibility alias for newer frontend pantry UI.
    // Current backend consume/remove logic is still package-count based.
    // If the frontend sends { "amount": 1 }, getQuantity() will return 1.
    private Integer amount;

    public Integer getQuantity() {
        return quantity != null ? quantity : amount;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getAmount() {
        return amount != null ? amount : quantity;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }
}
