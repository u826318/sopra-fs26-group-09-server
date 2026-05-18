package ch.uzh.ifi.hase.soprafs26.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import ch.uzh.ifi.hase.soprafs26.entity.PantryItem;

class MealPortionEstimateServiceTest {

    private MealPortionEstimateService service;

    @BeforeEach
    void setUp() {
        service = new MealPortionEstimateService(mock(WebClient.class));
    }

    @Test
    void estimatePortion_throwsWhenImageIsEmpty() {
        PantryItem item = new PantryItem();
        item.setId(10L);

        MockMultipartFile emptyImage = new MockMultipartFile(
                "image",
                "empty.png",
                "image/png",
                new byte[0]
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.estimatePortion(item, emptyImage)
        );

        assertEquals("Meal photo must not be empty.", exception.getMessage());
    }
}