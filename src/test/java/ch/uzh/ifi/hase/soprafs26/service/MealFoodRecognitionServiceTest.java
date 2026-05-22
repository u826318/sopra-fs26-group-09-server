package ch.uzh.ifi.hase.soprafs26.service;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import ch.uzh.ifi.hase.soprafs26.rest.dto.MealFoodRecognitionResponseDTO;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class MealFoodRecognitionServiceTest {

    private MealFoodRecognitionService mealFoodRecognitionService;
    private WebClient webClient;

    @BeforeEach
    public void setup() {
        webClient = Mockito.mock(WebClient.class);
        mealFoodRecognitionService = new MealFoodRecognitionService(webClient);
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
        // WebClient.post() returns null by default (mock), so the exception branch fires
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
        // When WebClient chain throws (mock returns null), fallback message is set correctly
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
    }

    @Test
    public void recognizeFood_fallbackDetectedFoods_isEmpty() {
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
    }

    @Test
    public void dto_settersAndGetters_work() {

        MealFoodRecognitionResponseDTO dto =
                new MealFoodRecognitionResponseDTO();

        dto.setStatus("RECOGNIZED");
        dto.setDetectedFoods(List.of("rice", "chicken"));
        dto.setMessage("Detected rice and chicken.");

        assertEquals("RECOGNIZED", dto.getStatus());

        assertEquals(
                2,
                dto.getDetectedFoods().size()
        );

        assertEquals(
                "Detected rice and chicken.",
                dto.getMessage()
        );
    }
}