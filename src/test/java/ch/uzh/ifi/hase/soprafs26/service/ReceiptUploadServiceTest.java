package ch.uzh.ifi.hase.soprafs26.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.entity.Household;
import ch.uzh.ifi.hase.soprafs26.entity.HouseholdMemberId;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdMemberRepository;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ProductDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ReceiptAnalysisResponseDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ReceiptLineItemDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ReceiptUploadResponseDTO;

class ReceiptUploadServiceTest {

    private HouseholdRepository householdRepository;
    private HouseholdMemberRepository householdMemberRepository;
    private ReceiptOcrService receiptOcrService;
    private OpenFoodFactsService openFoodFactsService;
    private ReceiptUploadService receiptUploadService;

    @BeforeEach
    void setUp() {
        householdRepository = mock(HouseholdRepository.class);
        householdMemberRepository = mock(HouseholdMemberRepository.class);
        receiptOcrService = mock(ReceiptOcrService.class);
        openFoodFactsService = mock(OpenFoodFactsService.class);

        receiptUploadService = new ReceiptUploadService(
                householdRepository,
                householdMemberRepository,
                receiptOcrService,
                openFoodFactsService
        );
    }

    @Test
    void uploadReceipt_success_matchesByProductCode() {
        mockMembership(true);

        ReceiptLineItemDTO item = receiptItem("Milk 1L", "7612345678901");
        ReceiptAnalysisResponseDTO analysis = receiptAnalysis(List.of(item));
        ProductDTO product = product("7612345678901", "Milk");

        when(receiptOcrService.analyzeReceipt(any())).thenReturn(analysis);
        when(openFoodFactsService.lookupByBarcode("7612345678901")).thenReturn(product);

        ReceiptUploadResponseDTO response = receiptUploadService.uploadReceipt(1L, 99L, jpgImage());

        assertEquals(1L, response.getHouseholdId());
        assertEquals("succeeded", response.getStatus());
        assertEquals("Migros", response.getMerchantName());
        assertEquals(1, response.getItems().size());
        assertEquals("MATCHED_BY_PRODUCT_CODE", response.getItems().get(0).getMatchStatus());
        assertEquals("openfoodfacts_barcode", response.getItems().get(0).getMatchSource());
        assertEquals("Milk", response.getItems().get(0).getMatchedProduct().getName());
    }

    @Test
    void uploadReceipt_whenBarcodeMisses_matchesByDescription() {
        mockMembership(true);

        ReceiptLineItemDTO item = receiptItem("Apple Juice", "999");
        ReceiptAnalysisResponseDTO analysis = receiptAnalysis(List.of(item));
        ProductDTO product = product("123", "Apple Juice");

        when(receiptOcrService.analyzeReceipt(any())).thenReturn(analysis);
        when(openFoodFactsService.lookupByBarcode("999"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
        when(openFoodFactsService.search("Apple Juice", 1)).thenReturn(List.of(product));

        ReceiptUploadResponseDTO response = receiptUploadService.uploadReceipt(1L, 99L, jpgImage());

        assertEquals("MATCHED_BY_DESCRIPTION", response.getItems().get(0).getMatchStatus());
        assertEquals("openfoodfacts_search", response.getItems().get(0).getMatchSource());
        assertEquals("Apple Juice", response.getItems().get(0).getMatchedProduct().getName());
    }

    @Test
    void uploadReceipt_whenNoMatch_returnsNoMatchItem() {
        mockMembership(true);

        ReceiptLineItemDTO item = receiptItem("Unknown product", null);
        ReceiptAnalysisResponseDTO analysis = receiptAnalysis(List.of(item));

        when(receiptOcrService.analyzeReceipt(any())).thenReturn(analysis);
        when(openFoodFactsService.search("Unknown product", 1)).thenReturn(List.of());

        ReceiptUploadResponseDTO response = receiptUploadService.uploadReceipt(1L, 99L, jpgImage());

        assertEquals("NO_MATCH", response.getItems().get(0).getMatchStatus());
        assertNull(response.getItems().get(0).getMatchSource());
        assertNull(response.getItems().get(0).getMatchedProduct());
    }

    @Test
    void uploadReceipt_whenHouseholdDoesNotExist_returnsNotFound() {
        when(householdRepository.findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> receiptUploadService.uploadReceipt(1L, 99L, jpgImage())
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(receiptOcrService, never()).analyzeReceipt(any());
    }

    @Test
    void uploadReceipt_whenUserIsNotMember_returnsForbidden() {
        mockMembership(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> receiptUploadService.uploadReceipt(1L, 99L, jpgImage())
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(receiptOcrService, never()).analyzeReceipt(any());
    }

    @Test
    void uploadReceipt_whenFileIsEmpty_returnsBadRequest() {
        mockMembership(true);
        MockMultipartFile emptyImage = new MockMultipartFile("image", "receipt.jpg", "image/jpeg", new byte[0]);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> receiptUploadService.uploadReceipt(1L, 99L, emptyImage)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(receiptOcrService, never()).analyzeReceipt(any());
    }

    @Test
    void uploadReceipt_whenFileIsTooLarge_returnsPayloadTooLarge() {
        mockMembership(true);
        byte[] oversizedContent = new byte[(int) ReceiptUploadService.MAX_RECEIPT_IMAGE_BYTES + 1];
        MockMultipartFile image = new MockMultipartFile("image", "receipt.png", "image/png", oversizedContent);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> receiptUploadService.uploadReceipt(1L, 99L, image)
        );

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getStatusCode());
        verify(receiptOcrService, never()).analyzeReceipt(any());
    }

    @Test
    void uploadReceipt_whenContentTypeIsUnsupported_returnsBadRequest() {
        mockMembership(true);
        MockMultipartFile image = new MockMultipartFile("image", "receipt.gif", "image/gif", "fake".getBytes());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> receiptUploadService.uploadReceipt(1L, 99L, image)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(receiptOcrService, never()).analyzeReceipt(any());
    }

    @Test
    void uploadReceipt_whenExtensionIsUnsupported_returnsBadRequest() {
        mockMembership(true);
        MockMultipartFile image = new MockMultipartFile("image", "receipt.txt", "image/png", "fake".getBytes());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> receiptUploadService.uploadReceipt(1L, 99L, image)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(receiptOcrService, never()).analyzeReceipt(any());
    }

    private void mockMembership(boolean isMember) {
        Household household = new Household();
        household.setId(1L);

        when(householdRepository.findById(1L)).thenReturn(Optional.of(household));
        when(householdMemberRepository.existsById(any(HouseholdMemberId.class))).thenReturn(isMember);
    }

    private MockMultipartFile jpgImage() {
        return new MockMultipartFile("image", "receipt.jpg", "image/jpeg", "fake-jpg".getBytes());
    }

    private ReceiptAnalysisResponseDTO receiptAnalysis(List<ReceiptLineItemDTO> items) {
        ReceiptAnalysisResponseDTO analysis = new ReceiptAnalysisResponseDTO();
        analysis.setStatus("succeeded");
        analysis.setMerchantName("Migros");
        analysis.setItems(items);
        return analysis;
    }

    private ReceiptLineItemDTO receiptItem(String description, String productCode) {
        ReceiptLineItemDTO item = new ReceiptLineItemDTO();
        item.setDescription(description);
        item.setQuantity("1");
        item.setProductCode(productCode);
        return item;
    }

    private ProductDTO product(String barcode, String name) {
        ProductDTO product = new ProductDTO();
        product.setBarcode(barcode);
        product.setName(name);
        return product;
    }
}
