package ch.uzh.ifi.hase.soprafs26.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductDTO;
import ch.uzh.ifi.hase.soprafs26.service.OpenFoodFactsService;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetLookupService;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetProductMapper;

@SpringBootTest
@AutoConfigureMockMvc
class OpenFoodFactsIntegrationTest {

    private static final String TEST_TOKEN = "off-integration-test-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private OpenFoodFactsService openFoodFactsService;

    @MockitoBean
    private LocalDatasetLookupService localDatasetLookupService;

    @MockitoBean
    private LocalDatasetProductMapper localDatasetProductMapper;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        createAuthenticatedUser();
    }

    @Test
    void lookupByBarcode_offFails_returnsMappedProductFromLocalDataset() throws Exception {
        given(openFoodFactsService.lookupByBarcode("7610848492087"))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "not found in OFF"));

        LocalDatasetProductDTO dto = new LocalDatasetProductDTO();
        dto.setBarcode("7610848492087");
        dto.setName("Integration Test Chocolate");
        dto.setBrand("Test Brand");

        given(localDatasetLookupService.findRawRowByBarcode("7610848492087"))
                .willReturn(Optional.of(Map.of("code", "7610848492087")));
        given(localDatasetProductMapper.toDto(any())).willReturn(dto);

        mockMvc.perform(get("/products/lookup")
                        .header(HttpHeaders.AUTHORIZATION, TEST_TOKEN)
                        .param("barcode", "7610848492087")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barcode").value("7610848492087"))
                .andExpect(jsonPath("$.name").value("Integration Test Chocolate"))
                .andExpect(jsonPath("$.brand").value("Test Brand"));
    }

    @Test
    void lookupByBarcode_missingAuthorizationToken_returns401() throws Exception {
        mockMvc.perform(get("/products/lookup")
                        .param("barcode", "7610848492087")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lookupByBarcode_blankBarcode_returns400() throws Exception {
        mockMvc.perform(get("/products/lookup")
                        .header(HttpHeaders.AUTHORIZATION, TEST_TOKEN)
                        .param("barcode", "   ")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lookupByBarcode_notFoundInOffOrLocal_returns404() throws Exception {
        given(openFoodFactsService.lookupByBarcode("0000"))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "not found in OFF"));
        given(localDatasetLookupService.findRawRowByBarcode("0000")).willReturn(Optional.empty());

        mockMvc.perform(get("/products/lookup")
                        .header(HttpHeaders.AUTHORIZATION, TEST_TOKEN)
                        .param("barcode", "0000")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
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
