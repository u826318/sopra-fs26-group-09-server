package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.service.OpenFoodFactsService;
import ch.uzh.ifi.hase.soprafs26.service.LocalProductDatasetService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenFoodFactsIntegrationTest {

    private static final String TEST_TOKEN = "off-integration-test-token";
    private static final String OFF_USER_AGENT = "sopra-fs26-group-09-virtual-pantry/0.1 (OpenFoodFacts portal)";
    private static final String OFF_BASE = "https://world.openfoodfacts.org";
    private static final String OFF_FIELDS = "?fields=code,product_name,abbreviated_product_name,brands,quantity,serving_size,image_front_url,image_url,url,nutrition_grades,nutriscore_data,nutriments,stores,stores_tags,purchase_places,purchase_places_tags";

    @TestConfiguration
    static class LocalFallbackDisabledTestConfig {
        @Bean
        @Primary
        LocalProductDatasetService localProductDatasetService() {
            LocalProductDatasetService localFallback = Mockito.mock(LocalProductDatasetService.class);
            Mockito.when(localFallback.lookupByBarcode(Mockito.anyString())).thenReturn(Optional.empty());
            return localFallback;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OpenFoodFactsService openFoodFactsService;

    private MockRestServiceServer mockOffServer;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        createAuthenticatedUser();

        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(openFoodFactsService, "restTemplate");
        mockOffServer = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @AfterEach
    void verifyExternalApiExpectations() {
        mockOffServer.verify();
    }

    @Test
    void lookupByBarcode_authenticatedRequest_returnsMappedProductFromOpenFoodFacts() throws Exception {
        mockOffServer.expect(requestTo(OFF_BASE + "/api/v2/product/7610848492087" + OFF_FIELDS))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.USER_AGENT, OFF_USER_AGENT))
                .andRespond(withSuccess("""
                        {
                          "status": 1,
                          "product": {
                            "code": "7610848492087",
                            "product_name": "Integration Test Chocolate",
                            "brands": "Test Brand",
                            "quantity": "100 g",
                            "serving_size": "25 g",
                            "image_front_url": "https://example.com/front.jpg",
                            "url": "https://world.openfoodfacts.org/product/7610848492087",
                            "nutrition_grades": "b",
                            "stores": "Migros, Coop",
                            "stores_tags": ["migros", "coop"],
                            "purchase_places": "Zurich, Bern",
                            "nutriments": {
                              "energy-kcal_100g": 510,
                              "proteins_100g": 8
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/products/lookup")
                        .header(HttpHeaders.AUTHORIZATION, TEST_TOKEN)
                        .param("barcode", "7610848492087")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.barcode").value("7610848492087"))
                .andExpect(jsonPath("$.name").value("Integration Test Chocolate"))
                .andExpect(jsonPath("$.brand").value("Test Brand"))
                .andExpect(jsonPath("$.quantity").value("100 g"))
                .andExpect(jsonPath("$.servingSize").value("25 g"))
                .andExpect(jsonPath("$.nutriScore").value("b"))
                .andExpect(jsonPath("$.stores[0]").value("Migros"))
                .andExpect(jsonPath("$.stores[1]").value("Coop"))
                .andExpect(jsonPath("$.storeTags[0]").value("migros"))
                .andExpect(jsonPath("$.purchasePlaces[0]").value("Zurich"))
                .andExpect(jsonPath("$['nutriments']['energy-kcal_100g']").value(510));
    }

    @Test
    void lookupByBarcode_missingAuthorizationToken_returns401BeforeCallingOpenFoodFacts() throws Exception {
        mockMvc.perform(get("/products/lookup")
                        .param("barcode", "7610848492087")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lookupByBarcode_blankBarcode_returns400BeforeCallingOpenFoodFacts() throws Exception {
        mockMvc.perform(get("/products/lookup")
                        .header(HttpHeaders.AUTHORIZATION, TEST_TOKEN)
                        .param("barcode", "   ")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lookupByBarcode_openFoodFactsReportsMissingProduct_returns404() throws Exception {
        mockOffServer.expect(requestTo(OFF_BASE + "/api/v2/product/0000" + OFF_FIELDS))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.USER_AGENT, OFF_USER_AGENT))
                .andRespond(withSuccess("""
                        {
                          "status": 0
                        }
                        """, MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/products/lookup")
                        .header(HttpHeaders.AUTHORIZATION, TEST_TOKEN)
                        .param("barcode", "0000")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void lookupByBarcode_openFoodFactsServerError_returns502() throws Exception {
        mockOffServer.expect(requestTo(OFF_BASE + "/api/v2/product/5000" + OFF_FIELDS))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.USER_AGENT, OFF_USER_AGENT))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("OFF is temporarily unavailable"));

        mockMvc.perform(get("/products/lookup")
                        .header(HttpHeaders.AUTHORIZATION, TEST_TOKEN)
                        .param("barcode", "5000")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadGateway());
    }

    private void createAuthenticatedUser() {
        User user = new User();
        user.setName("OFF Integration Test User");
        user.setUsername("offIntegrationUser");
        user.setPassword("password123");
        user.setToken(TEST_TOKEN);
        user.setStatus(UserStatus.ONLINE);
        user.setCreatedAt(Instant.now());

        userRepository.saveAndFlush(user);
    }
}
