package ch.uzh.ifi.hase.soprafs26.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private ReceiptAbbreviationService receiptAbbreviationService;
    private ReceiptUploadService receiptUploadService;

    @BeforeEach
    void setUp() {
        householdRepository = mock(HouseholdRepository.class);
        householdMemberRepository = mock(HouseholdMemberRepository.class);
        receiptOcrService = mock(ReceiptOcrService.class);
        openFoodFactsService = mock(OpenFoodFactsService.class);
        receiptAbbreviationService = new ReceiptAbbreviationService();

        receiptUploadService = new ReceiptUploadService(
                householdRepository,
                householdMemberRepository,
                receiptOcrService,
                openFoodFactsService,
                receiptAbbreviationService
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
        when(openFoodFactsService.search("apple juice", 8)).thenReturn(List.of(product));

        ReceiptUploadResponseDTO response = receiptUploadService.uploadReceipt(1L, 99L, jpgImage());

        assertEquals("MATCHED_BY_DESCRIPTION", response.getItems().get(0).getMatchStatus());
        assertEquals("openfoodfacts_search", response.getItems().get(0).getMatchSource());
        assertEquals("HIGH", response.getItems().get(0).getMatchConfidence());
        assertEquals(1.0, response.getItems().get(0).getMatchScore());
        assertEquals("Apple Juice", response.getItems().get(0).getMatchedProduct().getName());
        assertEquals(1, response.getItems().get(0).getCandidateProducts().size());
        assertEquals("123", response.getItems().get(0).getSuggestedPantryItem().getBarcode());
        assertEquals("Apple Juice", response.getItems().get(0).getSuggestedPantryItem().getName());
        assertEquals(1, response.getItems().get(0).getSuggestedPantryItem().getQuantity());
        assertEquals(true, response.getItems().get(0).getSuggestedPantryItem().getReadyForBulkAdd());
    }

    @Test
    void uploadReceipt_whenNoMatch_returnsNoMatchItem() {
        mockMembership(true);

        ReceiptLineItemDTO item = receiptItem("Unknown product", null);
        ReceiptAnalysisResponseDTO analysis = receiptAnalysis(List.of(item));

        when(receiptOcrService.analyzeReceipt(any())).thenReturn(analysis);
        when(openFoodFactsService.search("unknown product", 8)).thenReturn(List.of());

        ReceiptUploadResponseDTO response = receiptUploadService.uploadReceipt(1L, 99L, jpgImage());

        assertEquals("NO_MATCH", response.getItems().get(0).getMatchStatus());
        assertNull(response.getItems().get(0).getMatchSource());
        assertNull(response.getItems().get(0).getMatchedProduct());
        assertEquals(List.of(), response.getItems().get(0).getCandidateProducts());
    }

    @Test
    void uploadReceipt_normalizesReceiptAbbreviationsAndRanksCandidates() {
        mockMembership(true);

        ReceiptLineItemDTO item = receiptItem("LG EGGS 12 CT", null);
        ReceiptAnalysisResponseDTO analysis = receiptAnalysis(List.of(item));
        ProductDTO best = product("111", "Large Eggs 12 Count");
        ProductDTO weaker = product("222", "Chocolate Egg Candy");

        when(receiptOcrService.analyzeReceipt(any())).thenReturn(analysis);
        when(openFoodFactsService.search("large egg", 8)).thenReturn(List.of(weaker, best));

        ReceiptUploadResponseDTO response = receiptUploadService.uploadReceipt(1L, 99L, jpgImage());

        assertEquals("large egg", response.getItems().get(0).getNormalizedDescription());
        assertEquals("MATCHED_BY_DESCRIPTION", response.getItems().get(0).getMatchStatus());
        assertEquals("Large Eggs 12 Count", response.getItems().get(0).getMatchedProduct().getName());
        assertEquals("Large Eggs 12 Count", response.getItems().get(0).getCandidateProducts().get(0).getProduct().getName());
        assertEquals("111", response.getItems().get(0).getCandidateProducts().get(0).getSuggestedPantryItem().getBarcode());
        assertEquals(true, response.getItems().get(0).getCandidateProducts().get(0).getSuggestedPantryItem().getReadyForBulkAdd());
        assertTrue(response.getItems().get(0).getCandidateProducts().get(0).getScore()
                >= response.getItems().get(0).getCandidateProducts().get(1).getScore());
    }

    @Test
    void uploadReceipt_ranksCoreGroceryMatchAboveFlavoredProduct() {
        mockMembership(true);

        ReceiptLineItemDTO item = receiptItem("BANANA BIO", null);
        ReceiptAnalysisResponseDTO analysis = receiptAnalysis(List.of(item));
        ProductDTO best = product("111", "Organic Bananas");
        ProductDTO weaker = product("222", "Organic Chocolate Cookie Flavored with Banana");

        when(receiptOcrService.analyzeReceipt(any())).thenReturn(analysis);
        when(openFoodFactsService.search("banana organic", 8)).thenReturn(List.of(weaker, best));

        ReceiptUploadResponseDTO response = receiptUploadService.uploadReceipt(1L, 99L, jpgImage());

        assertEquals("banana organic", response.getItems().get(0).getNormalizedDescription());
        assertEquals("Organic Bananas", response.getItems().get(0).getCandidateProducts().get(0).getProduct().getName());
        assertEquals("Organic Bananas", response.getItems().get(0).getMatchedProduct().getName());
        assertTrue(response.getItems().get(0).getCandidateProducts().get(0).getScore()
                > response.getItems().get(0).getCandidateProducts().get(1).getScore());
    }

    @Test
    void uploadReceipt_expandsReceiptShortNamesBeforeSearch() {
        mockMembership(true);

        ReceiptLineItemDTO item = receiptItem("CHK BRST (/kg)", null);
        ReceiptAnalysisResponseDTO analysis = receiptAnalysis(List.of(item));
        ProductDTO best = product("111", "Chicken Breast");

        when(receiptOcrService.analyzeReceipt(any())).thenReturn(analysis);
        when(openFoodFactsService.search("chicken breast", 8)).thenReturn(List.of(best));

        ReceiptUploadResponseDTO response = receiptUploadService.uploadReceipt(1L, 99L, jpgImage());

        assertEquals("chicken breast", response.getItems().get(0).getNormalizedDescription());
        assertEquals("Chicken Breast", response.getItems().get(0).getMatchedProduct().getName());
        assertEquals("HIGH", response.getItems().get(0).getMatchConfidence());
    }

    @Test
    void uploadReceipt_whenStapleSearchHasNoResult_returnsGenericFallbackCandidate() {
        mockMembership(true);

        ReceiptLineItemDTO item = receiptItem("APL JUICE 1L", null);
        ReceiptAnalysisResponseDTO analysis = receiptAnalysis(List.of(item));

        when(receiptOcrService.analyzeReceipt(any())).thenReturn(analysis);
        when(openFoodFactsService.search("apple juice", 8)).thenReturn(List.of());

        ReceiptUploadResponseDTO response = receiptUploadService.uploadReceipt(1L, 99L, jpgImage());

        assertEquals("POSSIBLE_MATCH", response.getItems().get(0).getMatchStatus());
        assertEquals("generic_receipt_fallback", response.getItems().get(0).getMatchSource());
        assertEquals("Apple Juice", response.getItems().get(0).getMatchedProduct().getName());
        assertEquals("receipt-generic:apple-juice", response.getItems().get(0).getSuggestedPantryItem().getBarcode());
        assertEquals("1l", response.getItems().get(0).getSuggestedPantryItem().getPackageQuantity());
        assertEquals(460.0, response.getItems().get(0).getSuggestedPantryItem().getKcalPerPackage());
        assertEquals(true, response.getItems().get(0).getSuggestedPantryItem().getReadyForBulkAdd());
    }

    @Test
    void uploadReceipt_whenMilkStapleHasRealOffCandidate_prefersRealProductOverGenericFallback() {
        mockMembership(true);

        ReceiptLineItemDTO item = receiptItem("MLK 1L 3.5%", null);
        ReceiptAnalysisResponseDTO analysis = receiptAnalysis(List.of(item));
        ProductDTO wholeMilk = product("111", "Whole Milk");
        wholeMilk.setQuantity("1L");
        wholeMilk.setCaloriesPerPackage(640.0);

        when(receiptOcrService.analyzeReceipt(any())).thenReturn(analysis);
        when(openFoodFactsService.search(eq("milk"), eq(8))).thenReturn(List.of(wholeMilk));
        when(openFoodFactsService.search(eq("MLK 1L 3.5%"), eq(8))).thenReturn(List.of());

        ReceiptUploadResponseDTO response = receiptUploadService.uploadReceipt(1L, 99L, jpgImage());

        assertEquals("MATCHED_BY_DESCRIPTION", response.getItems().get(0).getMatchStatus());
        assertEquals("openfoodfacts_search", response.getItems().get(0).getMatchSource());
        assertEquals("Whole Milk", response.getItems().get(0).getMatchedProduct().getName());
        assertEquals("111", response.getItems().get(0).getSuggestedPantryItem().getBarcode());
        assertEquals(640.0, response.getItems().get(0).getSuggestedPantryItem().getKcalPerPackage());
    }

    @Test
    void uploadReceipt_whenMediumEggStapleHasRealOffCandidate_prefersRealProductOverGenericFallback() {
        mockMembership(true);

        ReceiptLineItemDTO item = receiptItem("EGGS M 10PCS", null);
        ReceiptAnalysisResponseDTO analysis = receiptAnalysis(List.of(item));
        ProductDTO eggs = product("222", "Medium Eggs 10 Count");
        eggs.setQuantity("10pcs");
        eggs.setCaloriesPerPackage(700.0);

        when(receiptOcrService.analyzeReceipt(any())).thenReturn(analysis);
        when(openFoodFactsService.search(eq("egg medium"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("medium eggs"), eq(8))).thenReturn(List.of(eggs));

        ReceiptUploadResponseDTO response = receiptUploadService.uploadReceipt(1L, 99L, jpgImage());

        assertEquals("MATCHED_BY_DESCRIPTION", response.getItems().get(0).getMatchStatus());
        assertEquals("Medium Eggs 10 Count", response.getItems().get(0).getMatchedProduct().getName());
        assertEquals("222", response.getItems().get(0).getSuggestedPantryItem().getBarcode());
        assertEquals(700.0, response.getItems().get(0).getSuggestedPantryItem().getKcalPerPackage());
    }

    @Test
    void uploadReceipt_whenBasmatiRiceAndAppleJuiceHaveRealOffCandidates_prefersThemOverGenericFallback() {
        mockMembership(true);

        ReceiptLineItemDTO riceItem = receiptItem("RICE BASMATI 1KG", null);
        ReceiptLineItemDTO juiceItem = receiptItem("APL JUICE 1L", null);
        ReceiptAnalysisResponseDTO analysis = receiptAnalysis(List.of(riceItem, juiceItem));

        ProductDTO rice = product("333", "Basmati Rice");
        rice.setQuantity("1kg");
        rice.setCaloriesPerPackage(3600.0);
        ProductDTO juice = product("444", "Apple Juice");
        juice.setQuantity("1L");
        juice.setCaloriesPerPackage(460.0);

        when(receiptOcrService.analyzeReceipt(any())).thenReturn(analysis);
        when(openFoodFactsService.search(eq("rice basmati"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("basmati rice"), eq(8))).thenReturn(List.of(rice));
        when(openFoodFactsService.search(eq("RICE BASMATI 1KG"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("apple juice"), eq(8))).thenReturn(List.of(juice));
        when(openFoodFactsService.search(eq("APL JUICE 1L"), eq(8))).thenReturn(List.of());

        ReceiptUploadResponseDTO response = receiptUploadService.uploadReceipt(1L, 99L, jpgImage());

        assertEquals("Basmati Rice", response.getItems().get(0).getMatchedProduct().getName());
        assertEquals(3600.0, response.getItems().get(0).getSuggestedPantryItem().getKcalPerPackage());
        assertEquals("Apple Juice", response.getItems().get(1).getMatchedProduct().getName());
        assertEquals(460.0, response.getItems().get(1).getSuggestedPantryItem().getKcalPerPackage());
    }

    @Test
    void uploadReceipt_whenFreshProduceSearchReturnsProcessedItems_prefersGenericFallback() {
        mockMembership(true);

        ReceiptLineItemDTO item = receiptItem("POTATO 2KG", null);
        ReceiptAnalysisResponseDTO analysis = receiptAnalysis(List.of(item));
        ProductDTO crisps = product("111", "Lightly Sea Salted Crisps");
        crisps.setBrand("Tyrrell's");
        crisps.setQuantity("150g");
        ProductDTO pringles = product("222", "Pringles Original");
        pringles.setBrand("Pringles");
        pringles.setQuantity("165g");

        when(receiptOcrService.analyzeReceipt(any())).thenReturn(analysis);
        when(openFoodFactsService.search("potato", 8)).thenReturn(List.of(crisps, pringles));

        ReceiptUploadResponseDTO response = receiptUploadService.uploadReceipt(1L, 99L, jpgImage());

        assertEquals("Potatoes", response.getItems().get(0).getCandidateProducts().get(0).getProduct().getName());
        assertEquals("generic_receipt_fallback", response.getItems().get(0).getCandidateProducts().get(0).getMatchSource());
        assertEquals("receipt-generic:potato", response.getItems().get(0).getCandidateProducts().get(0).getSuggestedPantryItem().getBarcode());
        assertEquals("2kg", response.getItems().get(0).getCandidateProducts().get(0).getSuggestedPantryItem().getPackageQuantity());
    }

    @Test
    void uploadReceipt_whenFreshProduceHasNoSearchResult_returnsGenericFallbackCandidate() {
        mockMembership(true);

        ReceiptLineItemDTO item = receiptItem("CUCUMBER", null);
        ReceiptAnalysisResponseDTO analysis = receiptAnalysis(List.of(item));

        when(receiptOcrService.analyzeReceipt(any())).thenReturn(analysis);
        when(openFoodFactsService.search("cucumber", 8)).thenReturn(List.of());

        ReceiptUploadResponseDTO response = receiptUploadService.uploadReceipt(1L, 99L, jpgImage());

        assertEquals("Cucumber", response.getItems().get(0).getMatchedProduct().getName());
        assertEquals("receipt-generic:cucumber", response.getItems().get(0).getSuggestedPantryItem().getBarcode());
        assertEquals(50.0, response.getItems().get(0).getSuggestedPantryItem().getKcalPerPackage());
        assertEquals(true, response.getItems().get(0).getSuggestedPantryItem().getReadyForBulkAdd());
    }

    @Test
    void uploadReceipt_whenCommonStaplesFallback_estimatesCaloriesInsteadOfZero() {
        mockMembership(true);

        ReceiptLineItemDTO oatsItem = receiptItem("OATS 500G", null);
        ReceiptLineItemDTO cheeseItem = receiptItem("CHS CHDR 200G", null);
        ReceiptLineItemDTO coffeeItem = receiptItem("COFFEE BLND 250G", null);
        ReceiptLineItemDTO oilItem = receiptItem("OLIV OIL 500ML", null);
        ReceiptAnalysisResponseDTO analysis = receiptAnalysis(List.of(oatsItem, cheeseItem, coffeeItem, oilItem));

        when(receiptOcrService.analyzeReceipt(any())).thenReturn(analysis);
        when(openFoodFactsService.search(eq("oats"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("OATS 500G"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("cheese cheddar"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("cheddar cheese"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("CHS CHDR 200G"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("coffee blend"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("coffee"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("COFFEE BLND 250G"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("olive oil"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("OLIV OIL 500ML"), eq(8))).thenReturn(List.of());

        ReceiptUploadResponseDTO response = receiptUploadService.uploadReceipt(1L, 99L, jpgImage());

        assertEquals(1850.0, response.getItems().get(0).getSuggestedPantryItem().getKcalPerPackage());
        assertEquals(800.0, response.getItems().get(1).getSuggestedPantryItem().getKcalPerPackage());
        assertEquals(5.0, response.getItems().get(2).getSuggestedPantryItem().getKcalPerPackage());
        assertEquals(4100.0, response.getItems().get(3).getSuggestedPantryItem().getKcalPerPackage());
    }

    @Test
    void uploadReceipt_whenExpandedFamilyStaplesFallback_estimatesCaloriesForMoreCategories() {
        mockMembership(true);

        ReceiptLineItemDTO granolaItem = receiptItem("GRANOLA 500G", null);
        ReceiptLineItemDTO orangeJuiceItem = receiptItem("ORANGE JUICE 1L", null);
        ReceiptLineItemDTO fetaItem = receiptItem("FETA CHS 200G", null);
        ReceiptLineItemDTO penneItem = receiptItem("PSTA PENNE 500G", null);
        ReceiptLineItemDTO tomatoItem = receiptItem("TMTO CHERRY 250G", null);
        ReceiptLineItemDTO spinachItem = receiptItem("SPINACH BABY 200G", null);
        ReceiptAnalysisResponseDTO analysis = receiptAnalysis(List.of(
                granolaItem,
                orangeJuiceItem,
                fetaItem,
                penneItem,
                tomatoItem,
                spinachItem
        ));

        when(receiptOcrService.analyzeReceipt(any())).thenReturn(analysis);
        when(openFoodFactsService.search(eq("granola"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("GRANOLA 500G"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("orange juice"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("ORANGE JUICE 1L"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("feta cheese"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("FETA CHS 200G"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("pasta penne"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("penne pasta"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("PSTA PENNE 500G"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("tomato cherry"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("cherry tomato"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("TMTO CHERRY 250G"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("spinach baby"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("baby spinach"), eq(8))).thenReturn(List.of());
        when(openFoodFactsService.search(eq("SPINACH BABY 200G"), eq(8))).thenReturn(List.of());

        ReceiptUploadResponseDTO response = receiptUploadService.uploadReceipt(1L, 99L, jpgImage());

        assertEquals(2250.0, response.getItems().get(0).getSuggestedPantryItem().getKcalPerPackage());
        assertEquals(450.0, response.getItems().get(1).getSuggestedPantryItem().getKcalPerPackage());
        assertEquals(530.0, response.getItems().get(2).getSuggestedPantryItem().getKcalPerPackage());
        assertEquals(1800.0, response.getItems().get(3).getSuggestedPantryItem().getKcalPerPackage());
        assertEquals(137.5, response.getItems().get(4).getSuggestedPantryItem().getKcalPerPackage());
        assertEquals(46.0, response.getItems().get(5).getSuggestedPantryItem().getKcalPerPackage());
    }

    @Test
    void uploadReceipt_keepsLowConfidenceCandidateForUserReview() {
        mockMembership(true);

        ReceiptLineItemDTO item = receiptItem("Mystery grocery", null);
        ReceiptAnalysisResponseDTO analysis = receiptAnalysis(List.of(item));
        ProductDTO candidate = product("333", "Garden Tomato Sauce");

        when(receiptOcrService.analyzeReceipt(any())).thenReturn(analysis);
        when(openFoodFactsService.search("mystery grocery", 8)).thenReturn(List.of(candidate));

        ReceiptUploadResponseDTO response = receiptUploadService.uploadReceipt(1L, 99L, jpgImage());

        assertEquals("POSSIBLE_MATCH", response.getItems().get(0).getMatchStatus());
        assertEquals("LOW", response.getItems().get(0).getMatchConfidence());
        assertEquals("Garden Tomato Sauce", response.getItems().get(0).getCandidateProducts().get(0).getProduct().getName());
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
