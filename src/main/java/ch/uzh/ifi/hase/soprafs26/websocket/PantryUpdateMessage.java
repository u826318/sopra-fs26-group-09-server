package ch.uzh.ifi.hase.soprafs26.websocket;

public class PantryUpdateMessage {

    private String eventType;
    private Long householdId;
    private Long triggeredByUserId;
    private String triggeredByUsername;
    private String timestamp;
    private PantryItemPayload item;
    private Double newTotalCalories;

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public Long getHouseholdId() { return householdId; }
    public void setHouseholdId(Long householdId) { this.householdId = householdId; }

    public Long getTriggeredByUserId() { return triggeredByUserId; }
    public void setTriggeredByUserId(Long triggeredByUserId) { this.triggeredByUserId = triggeredByUserId; }

    public String getTriggeredByUsername() { return triggeredByUsername; }
    public void setTriggeredByUsername(String triggeredByUsername) { this.triggeredByUsername = triggeredByUsername; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public PantryItemPayload getItem() { return item; }
    public void setItem(PantryItemPayload item) { this.item = item; }

    public Double getNewTotalCalories() { return newTotalCalories; }
    public void setNewTotalCalories(Double newTotalCalories) { this.newTotalCalories = newTotalCalories; }

    public static class PantryItemPayload {
        private Long itemId;
        private String productName;
        private String barcode;
        private Double quantity;
        private String unit;
        private Double caloriesPerUnit;
        private Long addedByUserId;
        private String addedAt;

        public Long getItemId() { return itemId; }
        public void setItemId(Long itemId) { this.itemId = itemId; }

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }

        public String getBarcode() { return barcode; }
        public void setBarcode(String barcode) { this.barcode = barcode; }

        public Double getQuantity() { return quantity; }
        public void setQuantity(Double quantity) { this.quantity = quantity; }

        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }

        public Double getCaloriesPerUnit() { return caloriesPerUnit; }
        public void setCaloriesPerUnit(Double caloriesPerUnit) { this.caloriesPerUnit = caloriesPerUnit; }

        public Long getAddedByUserId() { return addedByUserId; }
        public void setAddedByUserId(Long addedByUserId) { this.addedByUserId = addedByUserId; }

        public String getAddedAt() { return addedAt; }
        public void setAddedAt(String addedAt) { this.addedAt = addedAt; }
    }
}
