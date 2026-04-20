package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ReceiptAnalysisResponseDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ReceiptLineItemDTO;
import ch.uzh.ifi.hase.soprafs26.service.BarcodeExtractionService;
import ch.uzh.ifi.hase.soprafs26.service.OpenFoodFactsService;
import ch.uzh.ifi.hase.soprafs26.service.ReceiptAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductReceiptAnalysisControllerTest {

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

  private static final String TEST_TOKEN = "receipt-token";

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
  void analyzeReceipt_authenticatedAndValidImage_returns200() throws Exception {
    MockMultipartFile image = new MockMultipartFile(
        "image",
        "receipt.png",
        MediaType.IMAGE_PNG_VALUE,
        "fake-image-content".getBytes()
    );

    ReceiptLineItemDTO item = new ReceiptLineItemDTO();
    item.setDescription("Milk");
    item.setQuantity("2");
    item.setPrice("1.20 CHF");
    item.setTotalPrice("2.40 CHF");

    ReceiptAnalysisResponseDTO response = new ReceiptAnalysisResponseDTO();
    response.setStatus("succeeded");
    response.setMerchantName("Migros");
    response.setTotal("12.50 CHF");
    response.setItems(List.of(item));

    given(receiptAnalysisService.analyzeReceipt(any())).willReturn(response);

    mockMvc.perform(multipart("/products/receipt/analyze")
            .file(image)
            .header("Authorization", TEST_TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("succeeded")))
        .andExpect(jsonPath("$.merchantName", is("Migros")))
        .andExpect(jsonPath("$.total", is("12.50 CHF")))
        .andExpect(jsonPath("$.items[0].description", is("Milk")));
  }

  @Test
  void analyzeReceipt_withoutToken_stillReturns200BecauseDebugPortalRouteIsPublic() throws Exception {
    MockMultipartFile image = new MockMultipartFile(
        "image",
        "receipt.png",
        MediaType.IMAGE_PNG_VALUE,
        "fake-image-content".getBytes()
    );

    ReceiptAnalysisResponseDTO response = new ReceiptAnalysisResponseDTO();
    response.setStatus("succeeded");
    given(receiptAnalysisService.analyzeReceipt(any())).willReturn(response);

    mockMvc.perform(multipart("/products/receipt/analyze").file(image))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("succeeded")));
  }
}
