package ch.uzh.ifi.hase.soprafs26.service;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.uzh.ifi.hase.soprafs26.rest.dto.MealFoodRecognitionResponseDTO;

@Service
public class MealFoodRecognitionService {

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    public MealFoodRecognitionService(WebClient openAIWebClient) {
        this.webClient = openAIWebClient;
    }

    public MealFoodRecognitionResponseDTO recognizeFood(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("Meal photo must not be empty.");
        }

        try {
            byte[] imageBytes = image.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            String prompt = """
                    You are a food recognition assistant.

                    Analyze the meal photo and identify visible edible food items.
                    Estimate practical nutrition values for adding one recognized food to a pantry.
                    Use only these units for suggestedAmount: g, ml, package.
                    Prefer kcalPer100g for solid foods and kcalPerServing only when per-100g is not realistic.
                    Keep estimates conservative and mark low confidence when uncertain.

                    Respond ONLY in JSON:

                    {
                      "detectedFoods": ["rice", "grilled chicken"],
                      "recognizedFoods": [
                        {
                          "name": "rice",
                          "kcalPer100g": 130,
                          "kcalPerServing": null,
                          "suggestedAmount": 100,
                          "unit": "g",
                          "confidence": 0.82
                        }
                      ],
                      "message": "Detected rice and grilled chicken."
                    }
                    """;

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of(
                                    "role", "user",
                                    "content", List.of(
                                            Map.of(
                                                    "type", "text",
                                                    "text", prompt
                                            ),
                                            Map.of(
                                                    "type", "image_url",
                                                    "image_url", Map.of(
                                                            "url", "data:image/jpeg;base64," + base64Image
                                                    )
                                            )
                                    )
                            )
                    ),
                    "max_tokens", 300
            );

            Map response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            List choices = (List) response.get("choices");
            Map firstChoice = (Map) choices.get(0);
            Map message = (Map) firstChoice.get("message");
            String content = (String) message.get("content");

            String cleanJson = content
                    .replace("```json", "")
                    .replace("```", "")
                    .trim();

            MealFoodRecognitionResponseDTO dto =
                    mapper.readValue(cleanJson, MealFoodRecognitionResponseDTO.class);

            dto.setStatus("RECOGNIZED");
            if (dto.getDetectedFoods() == null) {
                dto.setDetectedFoods(List.of());
            }
            if (dto.getRecognizedFoods() == null) {
                dto.setRecognizedFoods(List.of());
            }

            return dto;
        }
        catch (Exception e) {
            MealFoodRecognitionResponseDTO fallback = new MealFoodRecognitionResponseDTO();
            fallback.setStatus("MANUAL_FALLBACK");
            fallback.setDetectedFoods(List.of());
            fallback.setRecognizedFoods(List.of());
            fallback.setMessage("Automatic food recognition failed. Please enter the food manually.");
            return fallback;
        }
    }
}
