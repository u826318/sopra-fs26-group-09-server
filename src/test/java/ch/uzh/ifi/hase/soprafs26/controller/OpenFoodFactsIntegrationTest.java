package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.service.OpenFoodFactsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

import static org.hamcrest.Matchers.hasSize;
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
        mockOffServer.expect(requestTo(OFF_BASE + "/api/v2/product/7610848492087"))
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
    void search_authenticatedRequest_returnsResolvedAndFallbackProducts() throws Exception {
        mockOffServer.expect(requestTo(OFF_BASE + "/cgi/search.pl?search_terms=milk&search_simple=1&action=process&json=1&page_size=2"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.USER_AGENT, OFF_USER_AGENT))
                .andRespond(withSuccess("""
                        {
                          "products": [
                            {
                              "code": "111",
                              "product_name": "Partial Milk Hit",
                              "brands": "Search Brand",
                              "stores": "Lidl, Denner",
                              "nutriments": {"energy-kcal_100g": 64}
                            },
                            {
                              "code": "222",
                              "product_name": "Search Result To Resolve"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        mockOffServer.expect(requestTo(OFF_BASE + "/api/v2/product/111"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.USER_AGENT, OFF_USER_AGENT))
                .andRespond(withSuccess("""
                        {
                          "status": 0
                        }
                        """, MediaType.APPLICATION_JSON));

        mockOffServer.expect(requestTo(OFF_BASE + "/api/v2/product/222"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.USER_AGENT, OFF_USER_AGENT))
                .andRespond(withSuccess("""
                        {
                          "status": 1,
                          "product": {
                            "code": "222",
                            "product_name": "Resolved Milk Product",
                            "brands": "Resolved Brand",
                            "quantity": "1 l",
                            "nutrition_grades": "a",
                            "stores_tags": ["coop"]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/products/search")
                        .header(HttpHeaders.AUTHORIZATION, TEST_TOKEN)
                        .param("q", "milk")
                        .param("limit", "2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].barcode").value("111"))
                .andExpect(jsonPath("$[0].name").value("Partial Milk Hit"))
                .andExpect(jsonPath("$[0].brand").value("Search Brand"))
                .andExpect(jsonPath("$[0].stores[0]").value("Lidl"))
                .andExpect(jsonPath("$[0]['nutriments']['energy-kcal_100g']").value(64))
                .andExpect(jsonPath("$[1].barcode").value("222"))
                .andExpect(jsonPath("$[1].name").value("Resolved Milk Product"))
                .andExpect(jsonPath("$[1].brand").value("Resolved Brand"))
                .andExpect(jsonPath("$[1].quantity").value("1 l"))
                .andExpect(jsonPath("$[1].nutriScore").value("a"))
                .andExpect(jsonPath("$[1].storeTags[0]").value("coop"));
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
        mockOffServer.expect(requestTo(OFF_BASE + "/api/v2/product/0000"))
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
        mockOffServer.expect(requestTo(OFF_BASE + "/api/v2/product/5000"))
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
