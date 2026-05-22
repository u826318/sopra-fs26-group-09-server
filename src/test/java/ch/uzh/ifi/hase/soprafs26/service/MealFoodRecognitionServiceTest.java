package ch.uzh.ifi.hase.soprafs26.service;

import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import ch.uzh.ifi.hase.soprafs26.rest.dto.MealFoodRecognitionResponseDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.RecognizedFoodDTO;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class MealFoodRecognitionServiceTest {

    private MealFoodRecognitionService mealFoodRecognitionService;
    private WebClient webClient;
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @SuppressWarnings("rawtypes")
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void setup() throws Exception {
        webClient = mock(WebClient.class);
        requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);

        doReturn(requestBodyUriSpec).when(webClient).post();
        doReturn(requestBodyUriSpec).when(requestBodyUriSpec).uri(anyString());
        doReturn(requestHeadersSpec).when(requestBodyUriSpec).bodyValue(any());
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();

        mealFoodRecognitionService = new MealFoodRecognitionService(webClient);

        Field modelField = MealFoodRecognitionService.class.getDeclaredField("model");
        modelField.setAccessible(true);
        modelField.set(mealFoodRecognitionService, "gpt-4o-mini");
    }

    @Test
    public void recognizeFood_emptyImage_throwsException() {

        MultipartFile image =
                new MockMultipartFile(
                        "image",
                        new byte[0]
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> mealFoodRecognitionService.recognizeFood(image)
        );
    }

    @Test
    public void recognizeFood_nullImage_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mealFoodRecognitionService.recognizeFood(null)
        );
    }

    @Test
    public void recognizeFood_fallbackResponse_notNull() {

        MultipartFile image =
                new MockMultipartFile(
                        "image",
                        "meal.jpg",
                        "image/jpeg",
                        "fake-image".getBytes()
                );

        MealFoodRecognitionResponseDTO result =
                mealFoodRecognitionService.recognizeFood(image);

        assertNotNull(result);
        assertNotNull(result.getStatus());
    }

    @Test
    public void recognizeFood_fallbackResponse_hasFallbackStatus() {
        doThrow(new RuntimeException("Network failure")).when(responseSpec).bodyToMono(any(Class.class));

        MultipartFile image =
                new MockMultipartFile(
                        "image",
                        "meal.jpg",
                        "image/jpeg",
                        "fake-image".getBytes()
                );

        MealFoodRecognitionResponseDTO result =
                mealFoodRecognitionService.recognizeFood(image);

        assertEquals("MANUAL_FALLBACK", result.getStatus());
        assertNotNull(result.getDetectedFoods());
        assertEquals(0, result.getDetectedFoods().size());
    }

    @Test
    public void recognizeFood_fallbackMessage_isCorrect() {
        doThrow(new RuntimeException("Network failure")).when(responseSpec).bodyToMono(any(Class.class));

        MultipartFile image =
                new MockMultipartFile(
                        "image",
                        "meal.jpg",
                        "image/jpeg",
                        "fake-image".getBytes()
                );

        MealFoodRecognitionResponseDTO result =
                mealFoodRecognitionService.recognizeFood(image);

        assertEquals("MANUAL_FALLBACK", result.getStatus());
        assertEquals("Automatic food recognition failed. Please enter the food manually.", result.getMessage());
        assertNotNull(result.getDetectedFoods());
        assertEquals(0, result.getDetectedFoods().size());
        assertNotNull(result.getRecognizedFoods());
        assertEquals(0, result.getRecognizedFoods().size());
    }

    @Test
    public void recognizeFood_fallbackDetectedFoods_isEmpty() {
        doThrow(new RuntimeException("Network failure")).when(responseSpec).bodyToMono(any(Class.class));

        MultipartFile image =
                new MockMultipartFile(
                        "image",
                        "meal.jpg",
                        "image/jpeg",
                        new byte[]{1, 2, 3}
                );

        MealFoodRecognitionResponseDTO result =
                mealFoodRecognitionService.recognizeFood(image);

        // The fallback always returns an empty list, never null
        assertNotNull(result.getDetectedFoods());
        assertEquals(0, result.getDetectedFoods().size());
        assertNotNull(result.getRecognizedFoods());
        assertEquals(0, result.getRecognizedFoods().size());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void recognizeFood_returnsRecognizedFoods_whenWebClientSucceeds() {
        String json = """
                {
                  "detectedFoods": ["rice"],
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
                  "message": "Detected rice."
                }
                """;
        Map<String, Object> response = Map.of(
                "choices", List.of(
                        Map.of("message", Map.of("content", json))
                )
        );
        doReturn(Mono.just(response)).when(responseSpec).bodyToMono(any(Class.class));

        MultipartFile image = new MockMultipartFile(
                "image",
                "meal.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );

        MealFoodRecognitionResponseDTO result = mealFoodRecognitionService.recognizeFood(image);

        assertEquals("RECOGNIZED", result.getStatus());
        assertEquals(List.of("rice"), result.getDetectedFoods());
        assertEquals(1, result.getRecognizedFoods().size());
        RecognizedFoodDTO recognizedFood = result.getRecognizedFoods().get(0);
        assertEquals("rice", recognizedFood.getName());
        assertEquals(130.0, recognizedFood.getKcalPer100g());
        assertEquals(100.0, recognizedFood.getSuggestedAmount());
        assertEquals("g", recognizedFood.getUnit());
        assertEquals(0.82, recognizedFood.getConfidence());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void recognizeFood_cleansMarkdownJsonWrapper() {
        String json = """
                ```json
                {
                  "detectedFoods": ["soup"],
                  "recognizedFoods": [],
                  "message": "Detected soup."
                }
                ```
                """;
        Map<String, Object> response = Map.of(
                "choices", List.of(
                        Map.of("message", Map.of("content", json))
                )
        );
        doReturn(Mono.just(response)).when(responseSpec).bodyToMono(any(Class.class));

        MultipartFile image = new MockMultipartFile(
                "image",
                "meal.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );

        MealFoodRecognitionResponseDTO result = mealFoodRecognitionService.recognizeFood(image);

        assertEquals("RECOGNIZED", result.getStatus());
        assertEquals(List.of("soup"), result.getDetectedFoods());
        assertEquals(0, result.getRecognizedFoods().size());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void recognizeFood_returnsManualFallback_whenJsonIsMalformed() {
        Map<String, Object> response = Map.of(
                "choices", List.of(
                        Map.of("message", Map.of("content", "{not-json"))
                )
        );
        doReturn(Mono.just(response)).when(responseSpec).bodyToMono(any(Class.class));

        MultipartFile image = new MockMultipartFile(
                "image",
                "meal.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );

        MealFoodRecognitionResponseDTO result = mealFoodRecognitionService.recognizeFood(image);

        assertEquals("MANUAL_FALLBACK", result.getStatus());
        assertEquals(0, result.getDetectedFoods().size());
        assertEquals(0, result.getRecognizedFoods().size());
    }

    @Test
    public void dto_settersAndGetters_work() {

        MealFoodRecognitionResponseDTO dto =
                new MealFoodRecognitionResponseDTO();

        dto.setStatus("RECOGNIZED");
        dto.setDetectedFoods(List.of("rice", "chicken"));
        RecognizedFoodDTO recognizedFood = new RecognizedFoodDTO();
        recognizedFood.setName("rice");
        recognizedFood.setKcalPer100g(130.0);
        dto.setRecognizedFoods(List.of(recognizedFood));
        dto.setMessage("Detected rice and chicken.");

        assertEquals("RECOGNIZED", dto.getStatus());

        assertEquals(
                2,
                dto.getDetectedFoods().size()
        );

        assertEquals("rice", dto.getRecognizedFoods().get(0).getName());

        assertEquals(
                "Detected rice and chicken.",
                dto.getMessage()
        );
    }
}
