package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class PortionEstimateResponseDTO {

    private String status;
    private String message;
    private Double suggestedMinAmount;
    private Double suggestedMaxAmount;
    private String unit;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Double getSuggestedMinAmount() {
        return suggestedMinAmount;
    }

    public void setSuggestedMinAmount(Double suggestedMinAmount) {
        this.suggestedMinAmount = suggestedMinAmount;
    }

    public Double getSuggestedMaxAmount() {
        return suggestedMaxAmount;
    }

    public void setSuggestedMaxAmount(Double suggestedMaxAmount) {
        this.suggestedMaxAmount = suggestedMaxAmount;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }
}