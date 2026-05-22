package ch.uzh.ifi.hase.soprafs26.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import ch.uzh.ifi.hase.soprafs26.entity.PantryItem;
import ch.uzh.ifi.hase.soprafs26.rest.dto.PortionEstimateResponseDTO;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

class MealPortionEstimateServiceTest {

    private MealPortionEstimateService service;

    private WebClient webClient;
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @SuppressWarnings("rawtypes")
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() throws Exception {
        webClient = mock(WebClient.class);
        requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);

        doReturn(requestBodyUriSpec).when(webClient).post();
        doReturn(requestBodyUriSpec).when(requestBodyUriSpec).uri(anyString());
        doReturn(requestHeadersSpec).when(requestBodyUriSpec).bodyValue(any());
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();

        service = new MealPortionEstimateService(webClient);

        // Inject the @Value field — Spring won't run in plain unit tests, so model stays null,
        // which causes Map.of("model", null, ...) to throw NPE before the WebClient is even called.
        Field modelField = MealPortionEstimateService.class.getDeclaredField("model");
        modelField.setAccessible(true);
        modelField.set(service, "gpt-4o-mini");
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

    @Test
    @SuppressWarnings("unchecked")
    void estimatePortion_returnsEstimated_whenWebClientSucceeds() {
        String json = "{\"suggestedMinAmount\":150,\"suggestedMaxAmount\":250,\"unit\":\"g\",\"message\":\"Rice portion.\"}";
        Map<String, Object> response = Map.of(
                "choices", List.of(
                        Map.of("message", Map.of("content", json))
                )
        );

        doReturn(Mono.just(response)).when(responseSpec).bodyToMono(any(Class.class));

        PantryItem item = new PantryItem();
        item.setId(1L);
        item.setAmountUnit("g");

        MockMultipartFile image = new MockMultipartFile(
                "image", "meal.jpg", "image/jpeg", new byte[]{1, 2, 3});

        PortionEstimateResponseDTO dto = service.estimatePortion(item, image);

        assertEquals("ESTIMATED", dto.getStatus());
        assertEquals(150.0, dto.getSuggestedMinAmount());
        assertEquals(250.0, dto.getSuggestedMaxAmount());
        assertEquals("g", dto.getUnit());
        assertEquals("Rice portion.", dto.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void estimatePortion_usesAmountUnitAsFallback_whenDtoUnitIsNull() {
        // JSON with no "unit" field — Jackson leaves it null; service should fill from pantryItem
        String json = "{\"suggestedMinAmount\":100,\"suggestedMaxAmount\":200,\"message\":\"Soup.\"}";
        Map<String, Object> response = Map.of(
                "choices", List.of(
                        Map.of("message", Map.of("content", json))
                )
        );

        doReturn(Mono.just(response)).when(responseSpec).bodyToMono(any(Class.class));

        PantryItem item = new PantryItem();
        item.setId(2L);
        item.setAmountUnit("ml");

        MockMultipartFile image = new MockMultipartFile(
                "image", "soup.jpg", "image/jpeg", new byte[]{4, 5, 6});

        PortionEstimateResponseDTO dto = service.estimatePortion(item, image);

        assertEquals("ESTIMATED", dto.getStatus());
        assertEquals("ml", dto.getUnit());
    }

    @Test
    @SuppressWarnings("unchecked")
    void estimatePortion_returnsManualFallback_whenWebClientThrows() {
        doThrow(new RuntimeException("Network failure")).when(responseSpec).bodyToMono(any(Class.class));

        PantryItem item = new PantryItem();
        item.setId(3L);
        item.setAmountUnit("package");

        MockMultipartFile image = new MockMultipartFile(
                "image", "food.jpg", "image/jpeg", new byte[]{7, 8, 9});

        PortionEstimateResponseDTO dto = service.estimatePortion(item, image);

        assertEquals("MANUAL_FALLBACK", dto.getStatus());
        assertNull(dto.getSuggestedMinAmount());
        assertNull(dto.getSuggestedMaxAmount());
        assertEquals("package", dto.getUnit());
        assertEquals("Automatic portion estimation failed. Please enter manually.", dto.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void estimatePortion_manualFallback_withNullPantryItem_unitIsNull() {
        doThrow(new RuntimeException("fail")).when(responseSpec).bodyToMono(any(Class.class));

        MockMultipartFile image = new MockMultipartFile(
                "image", "food.jpg", "image/jpeg", new byte[]{1});

        PortionEstimateResponseDTO dto = service.estimatePortion(null, image);

        assertEquals("MANUAL_FALLBACK", dto.getStatus());
        assertNull(dto.getUnit());
    }
}
