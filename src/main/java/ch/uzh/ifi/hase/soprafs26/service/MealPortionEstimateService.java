package ch.uzh.ifi.hase.soprafs26.service;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.uzh.ifi.hase.soprafs26.entity.PantryItem;
import ch.uzh.ifi.hase.soprafs26.rest.dto.PortionEstimateResponseDTO;

@Service
public class MealPortionEstimateService {

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    public MealPortionEstimateService(WebClient openAIWebClient) {
        this.webClient = openAIWebClient;
    }

    public PortionEstimateResponseDTO estimatePortion(PantryItem pantryItem, MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("Meal photo must not be empty.");
        }

        try {
            byte[] imageBytes = image.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            String prompt = """
                    You are a nutrition assistant.

                    Analyze the meal photo.

                    Tasks:
                    1. Identify the food.
                    2. Estimate the consumed portion size.
                    3. Return estimated minimum and maximum amount.
                    4. Use one of these units only: g, ml, package.
                    5. Be realistic and conservative.

                    Respond ONLY in JSON:

                    {
                      "suggestedMinAmount": 150,
                      "suggestedMaxAmount": 250,
                      "unit": "g",
                      "message": "Estimated rice portion with chicken."
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

            PortionEstimateResponseDTO dto = mapper.readValue(cleanJson, PortionEstimateResponseDTO.class);
            dto.setStatus("ESTIMATED");

            if (dto.getUnit() == null && pantryItem != null) {
                dto.setUnit(pantryItem.getAmountUnit());
            }

            return dto;
        }
        catch (Exception e) {
            return manualFallback(pantryItem);
        }
    }

    private PortionEstimateResponseDTO manualFallback(PantryItem pantryItem) {
        PortionEstimateResponseDTO fallback = new PortionEstimateResponseDTO();

        fallback.setStatus("MANUAL_FALLBACK");
        fallback.setSuggestedMinAmount(null);
        fallback.setSuggestedMaxAmount(null);
        fallback.setUnit(pantryItem != null ? pantryItem.getAmountUnit() : null);
        fallback.setMessage("Automatic portion estimation failed. Please enter manually.");

        return fallback;
    }
}