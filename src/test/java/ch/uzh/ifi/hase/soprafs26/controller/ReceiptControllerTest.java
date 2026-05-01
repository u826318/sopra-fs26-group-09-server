package ch.uzh.ifi.hase.soprafs26.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.config.AuthFilter;
import ch.uzh.ifi.hase.soprafs26.exceptions.GlobalExceptionAdvice;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ProductDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ReceiptMatchedItemDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ReceiptUploadResponseDTO;
import ch.uzh.ifi.hase.soprafs26.service.ReceiptUploadService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

@WebMvcTest(ReceiptController.class)
@Import(GlobalExceptionAdvice.class)
class ReceiptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReceiptUploadService receiptUploadService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AuthFilter authFilter;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);

            ((HttpServletRequest) request).setAttribute("authenticatedUserId", 99L);
            chain.doFilter(request, response);
            return null;
        }).when(authFilter).doFilter(any(), any(), any());
    }

    @Test
    void uploadReceipt_success_returnsMatchedItems() throws Exception {
        ProductDTO product = new ProductDTO();
        product.setBarcode("7612345678901");
        product.setName("Milk");

        ReceiptMatchedItemDTO item = new ReceiptMatchedItemDTO();
        item.setDescription("Milk 1L");
        item.setQuantity("1");
        item.setMatchStatus("MATCHED_BY_PRODUCT_CODE");
        item.setMatchedProduct(product);

        ReceiptUploadResponseDTO response = new ReceiptUploadResponseDTO();
        response.setHouseholdId(1L);
        response.setStatus("succeeded");
        response.setMerchantName("Migros");
        response.setItems(List.of(item));

        when(receiptUploadService.uploadReceipt(eq(1L), eq(99L), any(MultipartFile.class))).thenReturn(response);

        MockMultipartFile image = new MockMultipartFile(
                "image",
                "receipt.jpg",
                "image/jpeg",
                "fake-jpg".getBytes()
        );

        mockMvc.perform(multipart("/households/1/receipt/upload").file(image))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.householdId").value(1))
                .andExpect(jsonPath("$.status").value("succeeded"))
                .andExpect(jsonPath("$.merchantName").value("Migros"))
                .andExpect(jsonPath("$.items[0].description").value("Milk 1L"))
                .andExpect(jsonPath("$.items[0].matchStatus").value("MATCHED_BY_PRODUCT_CODE"))
                .andExpect(jsonPath("$.items[0].matchedProduct.barcode").value("7612345678901"));
    }

    @Test
    void uploadReceipt_whenServiceRejectsFile_returnsBadRequest() throws Exception {
        when(receiptUploadService.uploadReceipt(eq(1L), eq(99L), any(MultipartFile.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receipt image must be a JPG or PNG file."));

        MockMultipartFile image = new MockMultipartFile(
                "image",
                "receipt.gif",
                "image/gif",
                "fake-gif".getBytes()
        );

        mockMvc.perform(multipart("/households/1/receipt/upload").file(image))
                .andExpect(status().isBadRequest());
    }
}
