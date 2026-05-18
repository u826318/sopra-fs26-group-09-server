package ch.uzh.ifi.hase.soprafs26.service;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import ch.uzh.ifi.hase.soprafs26.rest.dto.MealFoodRecognitionResponseDTO;

public class MealFoodRecognitionServiceTest {

    private MealFoodRecognitionService mealFoodRecognitionService;

    @BeforeEach
    public void setup() {

        WebClient webClient = Mockito.mock(WebClient.class);

        mealFoodRecognitionService =
                new MealFoodRecognitionService(webClient);
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