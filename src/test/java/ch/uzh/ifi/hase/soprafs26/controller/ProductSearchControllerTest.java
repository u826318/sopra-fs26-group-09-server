package ch.uzh.ifi.hase.soprafs26.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ProductDTO;
import ch.uzh.ifi.hase.soprafs26.service.BarcodeExtractionService;
import ch.uzh.ifi.hase.soprafs26.service.OpenFoodFactsService;
import ch.uzh.ifi.hase.soprafs26.service.ReceiptAnalysisService;

@WebMvcTest(ProductController.class)
class ProductSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OpenFoodFactsService openFoodFactsService;

    @MockitoBean
    private BarcodeExtractionService barcodeExtractionService;

    @MockitoBean
    private ReceiptAnalysisService receiptAnalysisService;

    @MockitoBean
    private UserRepository userRepository;

    private static final String TEST_TOKEN = "search-test-token";

    @BeforeEach
    void setUp() {
        User authenticatedUser = new User();
        authenticatedUser.setId(1L);
        authenticatedUser.setUsername("testUser");
        authenticatedUser.setToken(TEST_TOKEN);
        authenticatedUser.setStatus(UserStatus.ONLINE);
        given(userRepository.findByToken(TEST_TOKEN)).willReturn(authenticatedUser);
    }

    @Test
    void search_withExplicitLimit_returnsResults() throws Exception {
        ProductDTO first = new ProductDTO();
        first.setBarcode("111");
        first.setName("Apple Juice");
        ProductDTO second = new ProductDTO();
        second.setBarcode("222");
        second.setName("Apple Yogurt");

        given(openFoodFactsService.search(eq("apple"), eq(2))).willReturn(List.of(first, second));

        mockMvc.perform(get("/products/search")
                        .header("Authorization", TEST_TOKEN)
                        .param("q", "apple")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("Apple Juice")))
                .andExpect(jsonPath("$[1].barcode", is("222")));
    }

    @Test
    void search_withoutLimit_usesDefaultLimit() throws Exception {
        given(openFoodFactsService.search(eq("milk"), eq(12))).willReturn(List.of());

        mockMvc.perform(get("/products/search")
                        .header("Authorization", TEST_TOKEN)
                        .param("q", "milk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(openFoodFactsService).search("milk", 12);
    }

    @Test
    void search_noToken_returns401() throws Exception {
        mockMvc.perform(get("/products/search").param("q", "apple"))
                .andExpect(status().isUnauthorized());
    }
}
