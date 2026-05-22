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
import ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductSearchCandidateDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductSearchResponseDTO;
import ch.uzh.ifi.hase.soprafs26.service.BarcodeExtractionService;
import ch.uzh.ifi.hase.soprafs26.service.OpenFoodFactsService;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetLookupService;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetNameSearchService;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetProductMapper;

@WebMvcTest(ProductController.class)
class ProductSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OpenFoodFactsService openFoodFactsService;

    @MockitoBean
    private BarcodeExtractionService barcodeExtractionService;

    @MockitoBean
    private LocalDatasetLookupService localDatasetLookupService;

    @MockitoBean
    private LocalDatasetProductMapper localDatasetProductMapper;

    @MockitoBean
    private LocalDatasetNameSearchService localDatasetNameSearchService;

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
        LocalDatasetProductSearchCandidateDTO c1 = new LocalDatasetProductSearchCandidateDTO();
        c1.setName("Apple Juice");
        LocalDatasetProductSearchCandidateDTO c2 = new LocalDatasetProductSearchCandidateDTO();
        c2.setName("Apple Yogurt");

        LocalDatasetProductSearchResponseDTO response = new LocalDatasetProductSearchResponseDTO();
        response.setCandidates(List.of(c1, c2));

        given(localDatasetNameSearchService.search(eq("apple"), eq(2))).willReturn(response);

        mockMvc.perform(get("/products/search")
                        .header("Authorization", TEST_TOKEN)
                        .param("q", "apple")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates", hasSize(2)))
                .andExpect(jsonPath("$.candidates[0].name", is("Apple Juice")))
                .andExpect(jsonPath("$.candidates[1].name", is("Apple Yogurt")));
    }

    @Test
    void search_withoutLimit_usesDefaultLimit() throws Exception {
        LocalDatasetProductSearchResponseDTO response = new LocalDatasetProductSearchResponseDTO();
        given(localDatasetNameSearchService.search(eq("milk"), eq(10))).willReturn(response);

        mockMvc.perform(get("/products/search")
                        .header("Authorization", TEST_TOKEN)
                        .param("q", "milk"))
                .andExpect(status().isOk());

        verify(localDatasetNameSearchService).search("milk", 10);
    }

    @Test
    void search_noToken_returns401() throws Exception {
        mockMvc.perform(get("/products/search").param("q", "apple"))
                .andExpect(status().isUnauthorized());
    }
}
