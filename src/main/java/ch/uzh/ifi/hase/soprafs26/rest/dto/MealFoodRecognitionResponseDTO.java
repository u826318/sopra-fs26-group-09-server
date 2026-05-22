package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.util.List;

public class MealFoodRecognitionResponseDTO {

    private String status;
    private List<String> detectedFoods;
    private List<RecognizedFoodDTO> recognizedFoods;
    private String message;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getDetectedFoods() {
        return detectedFoods;
    }

    public void setDetectedFoods(List<String> detectedFoods) {
        this.detectedFoods = detectedFoods;
    }

    public List<RecognizedFoodDTO> getRecognizedFoods() {
        return recognizedFoods;
    }

    public void setRecognizedFoods(List<RecognizedFoodDTO> recognizedFoods) {
        this.recognizedFoods = recognizedFoods;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
