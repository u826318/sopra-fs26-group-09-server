package ch.uzh.ifi.hase.soprafs26.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import ch.uzh.ifi.hase.soprafs26.rest.dto.ReceiptAnalysisResponseDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ReceiptLineItemDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ReceiptUploadResponseDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductSearchCandidateDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductSearchResponseDTO;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetNameSearchService;

class ReceiptUploadServiceTest {

    private HouseholdRepository householdRepository;
    private HouseholdMemberRepository householdMemberRepository;
    private ReceiptOcrService receiptOcrService;
    private LocalDatasetNameSearchService localDatasetNameSearchService;
    private ReceiptUploadService receiptUploadService;

    @BeforeEach
    void setUp() {
        householdRepository = mock(HouseholdRepository.class);
        householdMemberRepository = mock(HouseholdMemberRepository.class);
        receiptOcrService = mock(ReceiptOcrService.class);
        localDatasetNameSearchService = mock(LocalDatasetNameSearchService.class);

        receiptUploadService = new ReceiptUploadService(
                householdRepository,
                householdMemberRepository,
                receiptOcrService,
                localDatasetNameSearchService
        );
    }

    @Test
    void uploadReceipt_whenSearchReturnsCandidates_setsStatusCandidatesFoundAndMediumConfidence() {
        mockMembership(true);
        ReceiptLineItemDTO item = receiptItem("Milk 1L", null);
        when(receiptOcrService.analyzeReceipt(any())).thenReturn(receiptAnalysis(List.of(item)));
        LocalDatasetProductSearchResponseDTO search = searchResponse("OK", List.of(candidate("Whole Milk", 350.0)));
        when(localDatasetNameSearchService.search("Milk 1L", 10)).thenReturn(search);

        ReceiptUploadResponseDTO response = receiptUploadService.uploadReceipt(1L, 99L, jpgImage());

        assertEquals(1L, response.getHouseholdId());
        assertEquals("succeeded", response.getStatus());
        assertEquals("Migros", response.getMerchantName());
        assertEquals(1, response.getItems().size());
        assertEquals("CANDIDATES_FOUND", response.getItems().get(0).getMatchStatus());
        assertEquals("local_dataset_name_search", response.getItems().get(0).getMatchSource());
        assertEquals("MEDIUM", response.getItems().get(0).getMatchConfidence());
        assertEquals(350.0, response.getItems().get(0).getMatchScore());
        assertNull(response.getItems().get(0).getNormalizedDescription());
        assertNull(response.getItems().get(0).getMatchedProduct());
        assertNull(response.getItems().get(0).getSuggestedPantryItem());
        assertEquals(List.of(), response.getItems().get(0).getCandidateProducts());
        assertEquals(search, response.getItems().get(0).getProductSearch());
    }

    @Test
    void uploadReceipt_whenSearchStatusIsNotOkButHasCandidates_setsLowConfidence() {
        mockMembership(true);
        ReceiptLineItemDTO item = receiptItem("Pasta", null);
        when(receiptOcrService.analyzeReceipt(any())).thenReturn(receiptAnalysis(List.of(item)));
        LocalDatasetProductSearchResponseDTO search = searchResponse("TOO_MANY_MATCHES", List.of(candidate("Penne Pasta", 200.0)));
        when(localDatasetNameSearchService.search("Pasta", 10)).thenReturn(search);

        ReceiptUploadResponseDTO response = receiptUploadService.uploadReceipt(1L, 99L, jpgImage());

        assertEquals("CANDIDATES_FOUND", response.getItems().get(0).getMatchStatus());
        assertEquals("LOW", response.getItems().get(0).getMatchConfidence());
        assertEquals(200.0, response.getItems().get(0).getMatchScore());
    }

    @Test
    void uploadReceipt_whenSearchReturnsNoCandidates_setsStatusNoCandidates() {
        mockMembership(true);
        ReceiptLineItemDTO item = receiptItem("Xyzzy Product", null);
        when(receiptOcrService.analyzeReceipt(any())).thenReturn(receiptAnalysis(List.of(item)));
        LocalDatasetProductSearchResponseDTO search = searchResponse("NO_MATCH", List.of());
        when(localDatasetNameSearchService.search("Xyzzy Product", 10)).thenReturn(search);

        ReceiptUploadResponseDTO response = receiptUploadService.uploadReceipt(1L, 99L, jpgImage());

        assertEquals("NO_CANDIDATES", response.getItems().get(0).getMatchStatus());
        assertEquals("local_dataset_name_search", response.getItems().get(0).getMatchSource());
        assertEquals("LOW", response.getItems().get(0).getMatchConfidence());
        assertEquals(0.0, response.getItems().get(0).getMatchScore());
    }

    @Test
    void uploadReceipt_whenItemDescriptionIsNull_setsStatusNotSearchable() {
        mockMembership(true);
        ReceiptLineItemDTO item = receiptItem(null, null);
        when(receiptOcrService.analyzeReceipt(any())).thenReturn(receiptAnalysis(List.of(item)));

        ReceiptUploadResponseDTO response = receiptUploadService.uploadReceipt(1L, 99L, jpgImage());

        assertEquals("NOT_SEARCHABLE", response.getItems().get(0).getMatchStatus());
        assertEquals("local_dataset_name_search", response.getItems().get(0).getMatchSource());
        assertEquals("LOW", response.getItems().get(0).getMatchConfidence());
        assertEquals(0.0, response.getItems().get(0).getMatchScore());
        verify(localDatasetNameSearchService, never()).search(any(), anyInt());
    }

    @Test
    void uploadReceipt_whenSearchThrowsRuntimeException_setsStatusSearchFailed() {
        mockMembership(true);
        ReceiptLineItemDTO item = receiptItem("Bread", null);
        when(receiptOcrService.analyzeReceipt(any())).thenReturn(receiptAnalysis(List.of(item)));
        when(localDatasetNameSearchService.search("Bread", 10))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "unavailable"));

        ReceiptUploadResponseDTO response = receiptUploadService.uploadReceipt(1L, 99L, jpgImage());

        assertEquals("SEARCH_FAILED", response.getItems().get(0).getMatchStatus());
        assertEquals("local_dataset_name_search", response.getItems().get(0).getMatchSource());
        assertEquals("LOW", response.getItems().get(0).getMatchConfidence());
        assertEquals(0.0, response.getItems().get(0).getMatchScore());
    }

    @Test
    void uploadReceipt_whenNoItems_returnsEmptyItemList() {
        mockMembership(true);
        when(receiptOcrService.analyzeReceipt(any())).thenReturn(receiptAnalysis(List.of()));

        ReceiptUploadResponseDTO response = receiptUploadService.uploadReceipt(1L, 99L, jpgImage());

        assertEquals(List.of(), response.getItems());
        verify(localDatasetNameSearchService, never()).search(any(), anyInt());
    }

    @Test
    void uploadReceipt_whenMultipleItems_searchesEachSeparately() {
        mockMembership(true);
        ReceiptLineItemDTO item1 = receiptItem("Milk", null);
        ReceiptLineItemDTO item2 = receiptItem("Eggs", null);
        when(receiptOcrService.analyzeReceipt(any())).thenReturn(receiptAnalysis(List.of(item1, item2)));
        when(localDatasetNameSearchService.search("Milk", 10))
                .thenReturn(searchResponse("OK", List.of(candidate("Whole Milk", 400.0))));
        when(localDatasetNameSearchService.search("Eggs", 10))
                .thenReturn(searchResponse("NO_MATCH", List.of()));

        ReceiptUploadResponseDTO response = receiptUploadService.uploadReceipt(1L, 99L, jpgImage());

        assertEquals(2, response.getItems().size());
        assertEquals("CANDIDATES_FOUND", response.getItems().get(0).getMatchStatus());
        assertEquals("MEDIUM", response.getItems().get(0).getMatchConfidence());
        assertEquals("NO_CANDIDATES", response.getItems().get(1).getMatchStatus());
        assertEquals("LOW", response.getItems().get(1).getMatchConfidence());
    }

    @Test
    void uploadReceipt_itemFieldsAreCopiedFromOcrOutput() {
        mockMembership(true);
        ReceiptLineItemDTO item = receiptItem("Milk 1L", "7612345678901");
        item.setPrice("1.50");
        item.setTotalPrice("3.00");
        when(receiptOcrService.analyzeReceipt(any())).thenReturn(receiptAnalysis(List.of(item)));
        when(localDatasetNameSearchService.search("Milk 1L", 10))
                .thenReturn(searchResponse("OK", List.of(candidate("Milk", 300.0))));

        ReceiptUploadResponseDTO response = receiptUploadService.uploadReceipt(1L, 99L, jpgImage());

        assertEquals("Milk 1L", response.getItems().get(0).getDescription());
        assertEquals("7612345678901", response.getItems().get(0).getProductCode());
        assertEquals("1", response.getItems().get(0).getQuantity());
        assertEquals("1.50", response.getItems().get(0).getPrice());
        assertEquals("3.00", response.getItems().get(0).getTotalPrice());
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

    private LocalDatasetProductSearchResponseDTO searchResponse(String status, List<LocalDatasetProductSearchCandidateDTO> candidates) {
        LocalDatasetProductSearchResponseDTO response = new LocalDatasetProductSearchResponseDTO();
        response.setStatus(status);
        response.setCandidates(candidates);
        return response;
    }

    private LocalDatasetProductSearchCandidateDTO candidate(String name, double score) {
        LocalDatasetProductSearchCandidateDTO c = new LocalDatasetProductSearchCandidateDTO();
        c.setName(name);
        c.setScore(score);
        return c;
    }
}
