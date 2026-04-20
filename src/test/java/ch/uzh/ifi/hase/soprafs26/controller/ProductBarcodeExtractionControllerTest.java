package ch.uzh.ifi.hase.soprafs26.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.service.BarcodeExtractionService;
import ch.uzh.ifi.hase.soprafs26.service.OpenFoodFactsService;
import ch.uzh.ifi.hase.soprafs26.service.ReceiptAnalysisService;

@WebMvcTest(ProductController.class)
class ProductBarcodeExtractionControllerTest {

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

    private static final String TEST_TOKEN = "extract-token";

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
    void extractBarcode_authenticatedAndValidImage_returns200() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "barcode.png",
                MediaType.IMAGE_PNG_VALUE,
                "fake-image-content".getBytes());
        given(barcodeExtractionService.extractBarcode(any())).willReturn("7610848492087");

        mockMvc.perform(multipart("/products/barcode/extract")
                        .file(image)
                        .header("Authorization", TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barcode", is("7610848492087")));
    }

    @Test
    void extractBarcode_missingToken_returns401() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "barcode.png",
                MediaType.IMAGE_PNG_VALUE,
                "fake-image-content".getBytes());

        mockMvc.perform(multipart("/products/barcode/extract").file(image))
                .andExpect(status().isUnauthorized());
    }
}
