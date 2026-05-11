package ch.uzh.ifi.hase.soprafs26.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import ch.uzh.ifi.hase.soprafs26.entity.PantryItem;
import ch.uzh.ifi.hase.soprafs26.rest.dto.PortionEstimateResponseDTO;

class MealPortionEstimateServiceTest {

    private MealPortionEstimateService service;

    @BeforeEach
    void setUp() {
        service = new MealPortionEstimateService();
    }

    @Test
    void estimatePortion_returnsManualFallbackWhenCvServiceIsUnavailable() {
        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setName("Rice");
        item.setAmount(3.0);
        item.setAmountUnit("package");

        MockMultipartFile image = new MockMultipartFile(
                "image",
                "meal.png",
                "image/png",
                "fake-image".getBytes()
        );

        PortionEstimateResponseDTO result = service.estimatePortion(item, image);

        assertNull(result.getSuggestedAmount());
        assertNull(result.getEstimatedRange());
        assertTrue(result.isManualFallback());
        assertEquals(
                "Automatic portion estimation is currently unavailable. Please enter the consumed amount manually.",
                result.getMessage()
        );
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