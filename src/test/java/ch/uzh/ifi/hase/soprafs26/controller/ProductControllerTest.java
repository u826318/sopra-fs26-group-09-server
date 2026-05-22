package ch.uzh.ifi.hase.soprafs26.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.service.BarcodeExtractionService;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductDTO;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetLookupService;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetNameSearchService;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetProductMapper;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocalDatasetLookupService localDatasetLookupService;

    @MockitoBean
    private LocalDatasetProductMapper localDatasetProductMapper;

    @MockitoBean
    private LocalDatasetNameSearchService localDatasetNameSearchService;

    @MockitoBean
    private BarcodeExtractionService barcodeExtractionService;

    @MockitoBean
    private UserRepository userRepository;

    private static final String TEST_TOKEN = "test-token";

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
    void lookupByBarcodePath_found_returnsLocalDatasetProduct() throws Exception {
        LocalDatasetProductDTO localDto = new LocalDatasetProductDTO();
        localDto.setBarcode("7610848492087");
        localDto.setName("Local Product");

        given(localDatasetLookupService.findRawRowByBarcode("7610848492087"))
                .willReturn(Optional.of(Map.of("code", "7610848492087")));
        given(localDatasetProductMapper.toDto(any())).willReturn(localDto);

        mockMvc.perform(get("/products/lookup/7610848492087")
                        .header("Authorization", TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barcode", is("7610848492087")))
                .andExpect(jsonPath("$.name", is("Local Product")));
    }

    @Test
    void lookupByBarcodePath_notFound_returns404() throws Exception {
        given(localDatasetLookupService.findRawRowByBarcode("0000000000000"))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/products/lookup/0000000000000")
                        .header("Authorization", TEST_TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void lookupByBarcodePath_noToken_returns401() throws Exception {
        mockMvc.perform(get("/products/lookup/7610848492087"))
                .andExpect(status().isUnauthorized());
    }
}
