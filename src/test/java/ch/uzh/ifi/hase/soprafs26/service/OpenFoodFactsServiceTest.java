package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.rest.dto.ProductDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

class OpenFoodFactsServiceTest {

    private OpenFoodFactsService openFoodFactsService;
    private RestTemplate restTemplate;
    private LocalProductDatasetService localProductDatasetService;

    @BeforeEach
    void setup() {
        localProductDatasetService = Mockito.mock(LocalProductDatasetService.class);
        Mockito.when(localProductDatasetService.lookupByBarcode(anyString())).thenReturn(Optional.empty());

        openFoodFactsService = new OpenFoodFactsService(localProductDatasetService);
        restTemplate = Mockito.mock(RestTemplate.class);
        ReflectionTestUtils.setField(openFoodFactsService, "restTemplate", restTemplate);
    }

    @Test
    void lookupByBarcode_blankBarcode_throwsBadRequest() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> openFoodFactsService.lookupByBarcode("   "));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("barcode must not be empty", exception.getReason());
        Mockito.verifyNoInteractions(restTemplate);
    }

    @Test
    void lookupByBarcode_productFound_mapsFullResponse() {
        String body = """
                {
                  "status": 1,
                  "product": {
                    "code": "7612345678901",
                    "product_name": "Chocolate Bar",
                    "abbreviated_product_name": "Choco",
                    "brands": "Brand A",
                    "quantity": "100 g",
                    "serving_size": "25 g",
                    "image_front_url": "https://img/front.jpg",
                    "image_url": "https://img/fallback.jpg",
                    "url": "https://world.openfoodfacts.org/product/7612345678901",
                    "nutrition_grades": "a",
                    "stores": "Migros, Coop, Migros",
                    "stores_tags": ["migros", "coop", "migros"],
                    "purchase_places_tags": ["zurich", "bern"],
                    "nutriments": {"energy-kcal_100g": 510, "proteins_100g": 8},
                    "nutriscore_data": {"score": -2}
                  }
                }
                """;

        Mockito.when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        ProductDTO result = openFoodFactsService.lookupByBarcode("7612345678901");

        assertEquals("7612345678901", result.getBarcode());
        assertEquals("Chocolate Bar", result.getName());
        assertEquals("Brand A", result.getBrand());
        assertEquals("100 g", result.getQuantity());
        assertEquals("25 g", result.getServingSize());
        assertEquals("https://img/front.jpg", result.getImageUrl());
        assertEquals("https://world.openfoodfacts.org/product/7612345678901", result.getProductUrl());
        assertEquals("a", result.getNutriScore());
        assertEquals(List.of("Migros", "Coop"), result.getStores());
        assertEquals(List.of("migros", "coop"), result.getStoreTags());
        assertEquals(List.of("zurich", "bern"), result.getPurchasePlaces());
        assertNotNull(result.getNutriments());
        assertEquals(510, ((Number) result.getNutriments().get("energy-kcal_100g")).intValue());
        assertEquals(510.0, result.getCaloriesPerPackage(), 0.001);
        assertEquals(-2, ((Number) result.getNutriScoreData().get("score")).intValue());
        assertNotNull(result.getRawProduct());
        assertEquals("Chocolate Bar", result.getRawProduct().get("product_name"));

        Mockito.verify(restTemplate).exchange(contains("/api/v2/product/7612345678901"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(String.class));
    }

    @Test
    void lookupByBarcode_invalidJson_throwsBadGateway() {
        Mockito.when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{not-valid-json", HttpStatus.OK));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> openFoodFactsService.lookupByBarcode("7612345678901"));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        assertTrue(
        exception.getReason().startsWith(
                "OpenFoodFacts failed and local dataset had no fallback match for barcode 7612345678901"
        )
        );    }

    @Test
    void search_blankQuery_throwsBadRequest() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> openFoodFactsService.search("   ", 5));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("q must not be empty", exception.getReason());
        Mockito.verifyNoInteractions(restTemplate);
    }

    @Test
    void search_deduplicatesBarcodes_clampsLimit_andFallsBackToPartialHits() {
        String searchBody = """
                {
                  "products": [
                    {
                      "code": "111",
                      "product_name": "Hit One",
                      "brands": "Brand 1",
                      "image_url": "https://img/one.jpg",
                      "purchase_places": "Zurich, Geneva",
                      "nutriments": {"energy-kcal_100g": 123},
                      "nutriscore_data": {"score": 4}
                    },
                    {
                      "code": "111",
                      "product_name": "Duplicate Hit"
                    },
                    {
                      "code": "222",
                      "abbreviated_product_name": "Short Name",
                      "stores": ["Lidl", "Denner", "Lidl"],
                      "stores_tags": "lidl, denner",
                      "purchase_places_tags": ["basel", "bern"],
                      "url": "https://world.openfoodfacts.org/product/222"
                    },
                    {
                      "product_name": "Missing Barcode"
                    }
                  ]
                }
                """;

        String barcode111Lookup = """
                {
                  "status": 0
                }
                """;

        String barcode222Lookup = """
                {
                  "status": 1,
                  "product": {
                    "code": "222",
                    "product_name": "Resolved Product",
                    "brands": "Brand 2",
                    "quantity": "500 ml",
                    "serving_size": "250 ml",
                    "image_front_url": "https://img/two-front.jpg",
                    "nutrition_grades": "b",
                    "stores": "Aldi, Coop",
                    "stores_tags": ["aldi", "coop"],
                    "purchase_places": "Basel, Lausanne",
                    "nutriments": {"sugars_100g": 11},
                    "nutriscore_data": {"score": 1}
                  }
                }
                """;

        Mockito.when(restTemplate.exchange(contains("/cgi/search.pl"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(searchBody, HttpStatus.OK));
        Mockito.when(restTemplate.exchange(contains("/api/v2/product/111"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(barcode111Lookup, HttpStatus.OK));
        Mockito.when(restTemplate.exchange(contains("/api/v2/product/222"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(barcode222Lookup, HttpStatus.OK));

        List<ProductDTO> results = openFoodFactsService.search("  oat milk  ", 99);

        assertEquals(2, results.size());

        ProductDTO fallbackResult = results.get(0);
        assertEquals("111", fallbackResult.getBarcode());
        assertEquals("Hit One", fallbackResult.getName());
        assertEquals("Brand 1", fallbackResult.getBrand());
        assertEquals("https://img/one.jpg", fallbackResult.getImageUrl());
        assertEquals(List.of("Zurich", "Geneva"), fallbackResult.getPurchasePlaces());
        assertEquals(123, ((Number) fallbackResult.getNutriments().get("energy-kcal_100g")).intValue());
        assertEquals(4, ((Number) fallbackResult.getNutriScoreData().get("score")).intValue());

        ProductDTO resolvedResult = results.get(1);
        assertEquals("222", resolvedResult.getBarcode());
        assertEquals("Resolved Product", resolvedResult.getName());
        assertEquals("Brand 2", resolvedResult.getBrand());
        assertEquals("500 ml", resolvedResult.getQuantity());
        assertEquals("250 ml", resolvedResult.getServingSize());
        assertEquals("https://img/two-front.jpg", resolvedResult.getImageUrl());
        assertEquals("b", resolvedResult.getNutriScore());
        assertEquals(List.of("Aldi", "Coop"), resolvedResult.getStores());
        assertEquals(List.of("aldi", "coop"), resolvedResult.getStoreTags());
        assertEquals(List.of("Basel", "Lausanne"), resolvedResult.getPurchasePlaces());
        assertEquals(11, ((Number) resolvedResult.getNutriments().get("sugars_100g")).intValue());
        assertEquals(1, ((Number) resolvedResult.getNutriScoreData().get("score")).intValue());

        Mockito.verify(restTemplate).exchange(contains("page_size=12"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void lookupByBarcode_multipliedGramQuantity_estimatesCaloriesPerPackage() {
        String body = """
                {
                  "status": 1,
                  "product": {
                    "code": "333",
                    "product_name": "Snack Pack",
                    "quantity": "2 x 125 g",
                    "nutriments": {"energy-kcal_100g": 400}
                  }
                }
                """;

        Mockito.when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        ProductDTO result = openFoodFactsService.lookupByBarcode("333");

        assertEquals("Snack Pack", result.getName());
        assertEquals(1000.0, result.getCaloriesPerPackage(), 0.001);
    }

    @Test
    void lookupByBarcode_decimalLiterQuantity_estimatesCaloriesPerPackage() {
        String body = """
                {
                  "status": 1,
                  "product": {
                    "code": "444",
                    "product_name": "Juice Bottle",
                    "quantity": "1,5 l",
                    "nutriments": {"energy-kcal_100ml": 50}
                  }
                }
                """;

        Mockito.when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        ProductDTO result = openFoodFactsService.lookupByBarcode("444");

        assertEquals("Juice Bottle", result.getName());
        assertEquals(750.0, result.getCaloriesPerPackage(), 0.001);
    }

    @Test
    void search_invalidJson_throwsBadGateway() {
        Mockito.when(restTemplate.exchange(contains("/cgi/search.pl"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("not-json", HttpStatus.OK));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> openFoodFactsService.search("milk", 3));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        assertEquals("Failed to parse OpenFoodFacts search response", exception.getReason());
    }

    @Test
    void lookupByBarcode_non2xxResponse_throwsBadGateway() {
        Mockito.when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("failure", HttpStatus.INTERNAL_SERVER_ERROR));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> openFoodFactsService.lookupByBarcode("123"));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        assertTrue(
        exception.getReason().startsWith(
                "OpenFoodFacts failed and local dataset had no fallback match for barcode 123"
        )
        );
    }

    @Test
    void lookupByBarcode_restClientException_throwsBadGateway() {
        Mockito.when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("boom"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> openFoodFactsService.lookupByBarcode("123"));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        assertTrue(
        exception.getReason().startsWith(
                "OpenFoodFacts failed and local dataset had no fallback match for barcode 123"
        )
        );    }
}
