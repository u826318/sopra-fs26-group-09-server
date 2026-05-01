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
import ch.uzh.ifi.hase.soprafs26.rest.dto.ProductDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ReceiptAnalysisResponseDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ReceiptLineItemDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ReceiptMatchedItemDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ReceiptUploadResponseDTO;

@Service
public class ReceiptUploadService {

    public static final long MAX_RECEIPT_IMAGE_BYTES = 5L * 1024L * 1024L;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png");
    private static final String MATCHED_BY_PRODUCT_CODE = "MATCHED_BY_PRODUCT_CODE";
    private static final String MATCHED_BY_DESCRIPTION = "MATCHED_BY_DESCRIPTION";
    private static final String NO_MATCH = "NO_MATCH";

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final ReceiptOcrService receiptOcrService;
    private final OpenFoodFactsService openFoodFactsService;

    public ReceiptUploadService(
            HouseholdRepository householdRepository,
            HouseholdMemberRepository householdMemberRepository,
            ReceiptOcrService receiptOcrService,
            OpenFoodFactsService openFoodFactsService) {
        this.householdRepository = householdRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.receiptOcrService = receiptOcrService;
        this.openFoodFactsService = openFoodFactsService;
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
        response.setItems(matchItems(analysis.getItems()));
        return response;
    }

    private List<ReceiptMatchedItemDTO> matchItems(List<ReceiptLineItemDTO> extractedItems) {
        if (extractedItems == null || extractedItems.isEmpty()) {
            return List.of();
        }

        List<ReceiptMatchedItemDTO> matchedItems = new ArrayList<>(extractedItems.size());
        for (ReceiptLineItemDTO item : extractedItems) {
            matchedItems.add(matchItem(item));
        }
        return matchedItems;
    }

    private ReceiptMatchedItemDTO matchItem(ReceiptLineItemDTO item) {
        ReceiptMatchedItemDTO matchedItem = copyReceiptItem(item);

        ProductDTO barcodeMatch = tryLookupByBarcode(item.getProductCode());
        if (barcodeMatch != null) {
            matchedItem.setMatchedProduct(barcodeMatch);
            matchedItem.setMatchStatus(MATCHED_BY_PRODUCT_CODE);
            matchedItem.setMatchSource("openfoodfacts_barcode");
            return matchedItem;
        }

        ProductDTO descriptionMatch = trySearchByDescription(item.getDescription());
        if (descriptionMatch != null) {
            matchedItem.setMatchedProduct(descriptionMatch);
            matchedItem.setMatchStatus(MATCHED_BY_DESCRIPTION);
            matchedItem.setMatchSource("openfoodfacts_search");
            return matchedItem;
        }

        matchedItem.setMatchStatus(NO_MATCH);
        matchedItem.setMatchSource(null);
        return matchedItem;
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

    private ProductDTO tryLookupByBarcode(String productCode) {
        String sanitizedProductCode = blankToNull(productCode);
        if (sanitizedProductCode == null) {
            return null;
        }

        try {
            return openFoodFactsService.lookupByBarcode(sanitizedProductCode);
        }
        catch (ResponseStatusException exception) {
            return null;
        }
    }

    private ProductDTO trySearchByDescription(String description) {
        String sanitizedDescription = blankToNull(description);
        if (sanitizedDescription == null) {
            return null;
        }

        try {
            List<ProductDTO> results = openFoodFactsService.search(sanitizedDescription, 1);
            return results.isEmpty() ? null : results.get(0);
        }
        catch (ResponseStatusException exception) {
            return null;
        }
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
