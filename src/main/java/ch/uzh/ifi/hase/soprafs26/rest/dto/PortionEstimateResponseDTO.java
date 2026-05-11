package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class PortionEstimateResponseDTO {

    private Double suggestedAmount;
    private String estimatedRange;
    private String message;
    private boolean manualFallback;

    public Double getSuggestedAmount() {
        return suggestedAmount;
    }

    public void setSuggestedAmount(Double suggestedAmount) {
        this.suggestedAmount = suggestedAmount;
    }

    public String getEstimatedRange() {
        return estimatedRange;
    }

    public void setEstimatedRange(String estimatedRange) {
        this.estimatedRange = estimatedRange;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isManualFallback() {
        return manualFallback;
    }

    public void setManualFallback(boolean manualFallback) {
        this.manualFallback = manualFallback;
    }
}