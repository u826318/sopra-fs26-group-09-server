package ch.uzh.ifi.hase.soprafs26.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.entity.Household;
import ch.uzh.ifi.hase.soprafs26.entity.HouseholdMemberId;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdMemberRepository;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ReceiptAnalysisResponseDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ReceiptLineItemDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ReceiptMatchedItemDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ReceiptUploadResponseDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductSearchResponseDTO;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetNameSearchService;

@Service
public class ReceiptUploadService {

    public static final long MAX_RECEIPT_IMAGE_BYTES = 5L * 1024L * 1024L;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png");
    private static final int RECEIPT_NAME_SEARCH_LIMIT = 10;
    private static final String MATCH_SOURCE_LOCAL_NAME_SEARCH = "local_dataset_name_search";
    private static final String MATCH_STATUS_CANDIDATES_FOUND = "CANDIDATES_FOUND";
    private static final String MATCH_STATUS_NO_CANDIDATES = "NO_CANDIDATES";
    private static final String MATCH_STATUS_NOT_SEARCHABLE = "NOT_SEARCHABLE";
    private static final String MATCH_STATUS_SEARCH_FAILED = "SEARCH_FAILED";
    private static final String MEDIUM_CONFIDENCE = "MEDIUM";
    private static final String LOW_CONFIDENCE = "LOW";

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final ReceiptOcrService receiptOcrService;
    private final LocalDatasetNameSearchService localDatasetNameSearchService;

    public ReceiptUploadService(
            HouseholdRepository householdRepository,
            HouseholdMemberRepository householdMemberRepository,
            ReceiptOcrService receiptOcrService,
            LocalDatasetNameSearchService localDatasetNameSearchService) {
        this.householdRepository = householdRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.receiptOcrService = receiptOcrService;
        this.localDatasetNameSearchService = localDatasetNameSearchService;
    }

    public ReceiptUploadResponseDTO uploadReceipt(Long householdId, Long authenticatedUserId, MultipartFile image) {
        verifyHouseholdMembership(householdId, authenticatedUserId);
        validateReceiptImage(image);

        ReceiptAnalysisResponseDTO analysis = receiptOcrService.analyzeReceipt(image);
        return buildUploadResponse(householdId, analysis);
    }

    private void verifyHouseholdMembership(Long householdId, Long authenticatedUserId) {
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found."));

        HouseholdMemberId membershipId = new HouseholdMemberId(authenticatedUserId, household.getId());
        if (!householdMemberRepository.existsById(membershipId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a member of this household.");
        }
    }

    private void validateReceiptImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receipt image must not be empty.");
        }
        if (image.getSize() > MAX_RECEIPT_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Receipt image must not exceed 5 MB.");
        }

        String contentType = blankToNull(image.getContentType());
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receipt image must be a JPG or PNG file.");
        }

        String filename = blankToNull(image.getOriginalFilename());
        if (filename == null || !hasAllowedExtension(filename)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receipt image filename must end with .jpg, .jpeg, or .png.");
        }
    }

    private boolean hasAllowedExtension(String filename) {
        String lowerFilename = filename.toLowerCase(Locale.ROOT);
        return ALLOWED_EXTENSIONS.stream().anyMatch(lowerFilename::endsWith);
    }

    private ReceiptUploadResponseDTO buildUploadResponse(Long householdId, ReceiptAnalysisResponseDTO analysis) {
        ReceiptUploadResponseDTO response = new ReceiptUploadResponseDTO();
        response.setHouseholdId(householdId);
        response.setStatus(analysis.getStatus());
        response.setMerchantName(analysis.getMerchantName());
        response.setMerchantPhoneNumber(analysis.getMerchantPhoneNumber());
        response.setMerchantAddress(analysis.getMerchantAddress());
        response.setTransactionDate(analysis.getTransactionDate());
        response.setTransactionTime(analysis.getTransactionTime());
        response.setSubtotal(analysis.getSubtotal());
        response.setTax(analysis.getTax());
        response.setTotal(analysis.getTotal());
        response.setTip(analysis.getTip());
        response.setReceiptType(analysis.getReceiptType());
        response.setCurrencyCode(analysis.getCurrencyCode());
        response.setCountryRegion(analysis.getCountryRegion());
        response.setRawText(analysis.getRawText());
        response.setExtractedFields(analysis.getExtractedFields());
        response.setRawResult(analysis.getRawResult());
        response.setItems(attachLocalNameSearchCandidates(analysis.getItems()));
        return response;
    }

    private List<ReceiptMatchedItemDTO> attachLocalNameSearchCandidates(List<ReceiptLineItemDTO> extractedItems) {
        if (extractedItems == null || extractedItems.isEmpty()) {
            return List.of();
        }

        List<ReceiptMatchedItemDTO> matchedItems = new ArrayList<>(extractedItems.size());
        for (ReceiptLineItemDTO item : extractedItems) {
            matchedItems.add(attachLocalNameSearchCandidates(item));
        }
        return matchedItems;
    }

    private ReceiptMatchedItemDTO attachLocalNameSearchCandidates(ReceiptLineItemDTO item) {
        ReceiptMatchedItemDTO matchedItem = copyReceiptItem(item);
        String searchQuery = blankToNull(item.getDescription());
        matchedItem.setNormalizedDescription(null);
        matchedItem.setMatchSource(MATCH_SOURCE_LOCAL_NAME_SEARCH);
        matchedItem.setCandidateProducts(List.of());
        matchedItem.setMatchedProduct(null);
        matchedItem.setSuggestedPantryItem(null);

        if (searchQuery == null) {
            LocalDatasetProductSearchResponseDTO emptySearch = new LocalDatasetProductSearchResponseDTO();
            emptySearch.setStatus("NOT_ENOUGH_INFORMATION");
            emptySearch.setMessage("Azure OCR did not extract a searchable product name for this receipt line.");
            emptySearch.setTotalCandidateCount(0);
            emptySearch.setCandidates(List.of());
            matchedItem.setProductSearch(emptySearch);
            matchedItem.setMatchStatus(MATCH_STATUS_NOT_SEARCHABLE);
            matchedItem.setMatchConfidence(LOW_CONFIDENCE);
            matchedItem.setMatchScore(0.0);
            return matchedItem;
        }

        try {
            LocalDatasetProductSearchResponseDTO productSearch = localDatasetNameSearchService.search(searchQuery, RECEIPT_NAME_SEARCH_LIMIT);
            matchedItem.setProductSearch(productSearch);

            int candidateCount = productSearch.getCandidates() == null ? 0 : productSearch.getCandidates().size();
            if (candidateCount > 0) {
                matchedItem.setMatchStatus(MATCH_STATUS_CANDIDATES_FOUND);
                matchedItem.setMatchConfidence("OK".equals(productSearch.getStatus()) ? MEDIUM_CONFIDENCE : LOW_CONFIDENCE);
                matchedItem.setMatchScore(productSearch.getCandidates().get(0).getScore());
            }
            else {
                matchedItem.setMatchStatus(MATCH_STATUS_NO_CANDIDATES);
                matchedItem.setMatchConfidence(LOW_CONFIDENCE);
                matchedItem.setMatchScore(0.0);
            }
            return matchedItem;
        }
        catch (RuntimeException exception) {
            LocalDatasetProductSearchResponseDTO failedSearch = new LocalDatasetProductSearchResponseDTO();
            failedSearch.setQuery(searchQuery);
            failedSearch.setNormalizedQuery(searchQuery);
            failedSearch.setStatus("ERROR");
            failedSearch.setMessage("Local product name search is currently unavailable for this receipt line.");
            failedSearch.setTotalCandidateCount(0);
            failedSearch.setCandidates(List.of());
            matchedItem.setProductSearch(failedSearch);
            matchedItem.setMatchStatus(MATCH_STATUS_SEARCH_FAILED);
            matchedItem.setMatchConfidence(LOW_CONFIDENCE);
            matchedItem.setMatchScore(0.0);
            return matchedItem;
        }
    }

    private ReceiptMatchedItemDTO copyReceiptItem(ReceiptLineItemDTO item) {
        ReceiptMatchedItemDTO matchedItem = new ReceiptMatchedItemDTO();
        matchedItem.setDescription(item.getDescription());
        matchedItem.setQuantity(item.getQuantity());
        matchedItem.setPrice(item.getPrice());
        matchedItem.setTotalPrice(item.getTotalPrice());
        matchedItem.setProductCode(item.getProductCode());
        matchedItem.setRawItem(item.getRawItem());
        return matchedItem;
    }


    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
