package ch.uzh.ifi.hase.soprafs26.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import ch.uzh.ifi.hase.soprafs26.rest.dto.ReceiptPantryItemSuggestionDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ReceiptProductCandidateDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ReceiptUploadResponseDTO;

@Service
public class ReceiptUploadService {

    public static final long MAX_RECEIPT_IMAGE_BYTES = 5L * 1024L * 1024L;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png");
    private static final String MATCHED_BY_PRODUCT_CODE = "MATCHED_BY_PRODUCT_CODE";
    private static final String MATCHED_BY_DESCRIPTION = "MATCHED_BY_DESCRIPTION";
    private static final String POSSIBLE_MATCH = "POSSIBLE_MATCH";
    private static final String NO_MATCH = "NO_MATCH";
    private static final String HIGH_CONFIDENCE = "HIGH";
    private static final String MEDIUM_CONFIDENCE = "MEDIUM";
    private static final String LOW_CONFIDENCE = "LOW";
    private static final String APPLE_JUICE = "apple juice";
    private static final String ORANGE_JUICE = "orange juice";
    private static final String GREEN_TEA = "green tea";
    private static final String BLACK_TEA = "black tea";
    private static final String ICED_TEA = "iced tea";
    private static final String MILK_TEA = "milk tea";
    private static final String HERBAL_TEA = "herbal tea";
    private static final String MINERAL_WATER = "mineral water";
    private static final String SPARKLING_WATER = "sparkling water";
    private static final String STILL_WATER = "still water";
    private static final String BASMATI_RICE = "basmati rice";
    private static final String CHEDDAR_CHEESE = "cheddar cheese";
    private static final String WHOLE_BREAD = "whole bread";
    private static final String WHEAT_BREAD = "wheat bread";
    private static final String CHERRY_TOMATO = "cherry tomato";
    private static final String BABY_SPINACH = "baby spinach";
    private static final String GREEK_YOGURT = "greek yogurt";
    private static final String STRAWBERRY_YOGURT = "strawberry yogurt";
    private static final String PENNE_PASTA = "penne pasta";
    private static final String SPAGHETTI_PASTA = "spaghetti pasta";
    private static final int DESCRIPTION_SEARCH_LIMIT = 8;
    private static final int MAX_CANDIDATES = 5;
    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.78;
    private static final double MEDIUM_CONFIDENCE_THRESHOLD = 0.48;
    private static final Set<String> PACKAGE_QUANTITY_UNITS = Set.of(
            "kg", "g", "mg", "ml", "cl", "dl", "l", "lb", "lbs", "oz", "pcs", "pc", "ct"
    );
    private static final Set<String> PRODUCT_TYPE_TOKENS = Set.of(
            "banana", "apple", "avocado", "bread", "butter", "cheese", "chicken", "coffee",
            "cookie", "cookies", "egg", "eggs", "juice", "milk", "oil", "oats", "pasta",
            "rice", "sauce", "spaghetti", "tomato", "yogurt", "bar", "chips", "cereal",
            "beef", "pork", "fish", "water"
    );
    private static final Set<String> GENERIC_GROCERY_TOKENS = Set.of(
            "apple", "avocado", "banana", "basmati", "baby", "bread", "cheddar", "cheese", "cherry",
            "chicken", "coffee", "cucumber", "egg", "granola", "herbal", "iced", "juice", "milk", "muesli",
            "oats", "oil", "olive", "orange", "pasta", "penne", "potato", "rice", "sauce",
            "spinach", "strawberry", "tea", "tomato", "water", "whole", "wheat", "yogurt"
    );
    private static final Set<String> STAPLE_SEARCH_TOKENS = Set.of(
            "apple", "basmati", "bread", "cheddar", "cheese", "coffee", "egg", "granola",
            "juice", "medium", "milk", "mineral", "natural", "oats", "olive", "orange",
            "black", "green", "herbal", "iced", "tea",
            "pasta", "penne", "rice", "sauce", "sparkling", "spaghetti", "still", "tomato",
            "water", "whole", "wheat", "yogurt"
    );
    private static final Set<String> FRESH_PRODUCE_TOKENS = Set.of(
            "apple", "banana", "broccoli", "carrot", "cucumber", "lettuce", "onion",
            "pepper", "potato", "spinach", "tomato", "zucchini", "avocado"
    );
    private static final Set<String> NON_PRODUCE_CONTEXT_TOKENS = Set.of(
            "bread", "cheddar", "cheese", "coffee", "egg", "juice", "milk", "oats",
            "oil", "pasta", "rice", "sauce", "yogurt"
    );
    private static final Set<String> PROCESSED_PRODUCT_TOKENS = Set.of(
            "bar", "biscuit", "breaded", "cake", "candy", "chips", "cookie", "cookies",
            "crisps", "dessert", "drink", "flavored", "flavour", "juice", "mayo",
            "pringles", "protein", "sauce", "shake", "snack", "soup"
    );
    private static final Set<String> FRUIT_TOKENS = Set.of(
            "apple", "apricot", "avocado", "banana", "blueberry", "cherry", "grape",
            "kiwi", "lemon", "lime", "mango", "melon", "orange", "pear", "pineapple",
            "plum", "raspberry", "strawberry", "watermelon"
    );
    private static final Set<String> VEGETABLE_TOKENS = Set.of(
            "broccoli", "cabbage", "carrot", "cauliflower", "celery", "cucumber", "garlic",
            "lettuce", "mushroom", "onion", "pepper", "potato", "spinach", "tomato",
            "zucchini"
    );
    private static final Set<String> DAIRY_TOKENS = Set.of(
            "butter", "cheese", "cheddar", "cream", "egg", "eggs", "milk", "mozzarella",
            "feta", "gouda", "parmesan", "yogurt", "yoghurt"
    );
    private static final Set<String> GRAIN_TOKENS = Set.of(
            "bagel", "barley", "bread", "bun", "cereal", "cornflakes", "granola", "muesli",
            "noodles", "oats", "pasta", "penne", "rice", "spaghetti", "toast"
    );
    private static final Set<String> DRINK_TOKENS = Set.of(
            "coffee", "juice", "milk", "mineral", "smoothie", "soda", "sparkling", "still",
            "tea", "water"
    );
    private static final Set<String> SAUCE_TOKENS = Set.of(
            "ketchup", "mayo", "mayonnaise", "mustard", "pesto", "sauce", "soup"
    );
    private static final Set<String> OIL_TOKENS = Set.of(
            "butter", "margarine", "oil", "olive"
    );

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final ReceiptOcrService receiptOcrService;
    private final OpenFoodFactsService openFoodFactsService;
    private final ReceiptAbbreviationService receiptAbbreviationService;

    public ReceiptUploadService(
            HouseholdRepository householdRepository,
            HouseholdMemberRepository householdMemberRepository,
            ReceiptOcrService receiptOcrService,
            OpenFoodFactsService openFoodFactsService,
            ReceiptAbbreviationService receiptAbbreviationService) {
        this.householdRepository = householdRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.receiptOcrService = receiptOcrService;
        this.openFoodFactsService = openFoodFactsService;
        this.receiptAbbreviationService = receiptAbbreviationService;
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
        String normalizedDescription = receiptAbbreviationService.normalize(item.getDescription());
        matchedItem.setNormalizedDescription(blankToNull(normalizedDescription));

        ProductDTO barcodeMatch = tryLookupByBarcode(item.getProductCode());
        if (barcodeMatch != null) {
            ReceiptProductCandidateDTO barcodeCandidate = buildCandidate(
                    barcodeMatch,
                    1.0,
                    HIGH_CONFIDENCE,
                    "openfoodfacts_barcode"
            );
            matchedItem.setMatchedProduct(barcodeMatch);
            matchedItem.setMatchStatus(MATCHED_BY_PRODUCT_CODE);
            matchedItem.setMatchSource("openfoodfacts_barcode");
            matchedItem.setMatchConfidence(HIGH_CONFIDENCE);
            matchedItem.setMatchScore(1.0);
            matchedItem.setSuggestedPantryItem(barcodeCandidate.getSuggestedPantryItem());
            matchedItem.setCandidateProducts(addBarcodeCandidateFirst(
                    barcodeCandidate,
                    searchDescriptionCandidates(item.getDescription(), normalizedDescription)
            ));
            return matchedItem;
        }

        List<ReceiptProductCandidateDTO> candidates = searchDescriptionCandidates(item.getDescription(), normalizedDescription);
        matchedItem.setCandidateProducts(candidates);
        if (!candidates.isEmpty()) {
            ReceiptProductCandidateDTO topCandidate = candidates.get(0);
            matchedItem.setMatchedProduct(topCandidate.getProduct());
            matchedItem.setMatchSource(topCandidate.getMatchSource());
            matchedItem.setMatchConfidence(topCandidate.getConfidence());
            matchedItem.setMatchScore(topCandidate.getScore());
            matchedItem.setSuggestedPantryItem(topCandidate.getSuggestedPantryItem());
            matchedItem.setMatchStatus(
                    HIGH_CONFIDENCE.equals(topCandidate.getConfidence()) ? MATCHED_BY_DESCRIPTION : POSSIBLE_MATCH
            );
            return matchedItem;
        }

        matchedItem.setMatchStatus(NO_MATCH);
        matchedItem.setMatchSource(null);
        matchedItem.setMatchConfidence(LOW_CONFIDENCE);
        matchedItem.setMatchScore(0.0);
        matchedItem.setCandidateProducts(List.of());
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

    private List<ReceiptProductCandidateDTO> searchDescriptionCandidates(String originalDescription, String normalizedDescription) {
        String searchQuery = firstNonBlank(normalizedDescription, originalDescription);
        if (searchQuery == null) {
            return List.of();
        }

        try {
            List<ProductDTO> products = searchOpenFoodFactsCandidates(searchQuery, originalDescription);
            return enrichWithGenericFallback(searchQuery, originalDescription, rankCandidates(searchQuery, originalDescription, products));
        }
        catch (ResponseStatusException exception) {
            return enrichWithGenericFallback(searchQuery, originalDescription, List.of());
        }
    }

    private List<ProductDTO> searchOpenFoodFactsCandidates(String searchQuery, String originalDescription) {
        List<String> queries = buildSearchQueries(searchQuery, originalDescription);
        Map<String, ProductDTO> uniqueProducts = new LinkedHashMap<>();

        for (String query : queries) {
            try {
                List<ProductDTO> products = openFoodFactsService.search(query, DESCRIPTION_SEARCH_LIMIT);
                if (products == null || products.isEmpty()) {
                    continue;
                }
                for (ProductDTO product : products) {
                    if (product == null) {
                        continue;
                    }
                    String candidateKey = firstNonBlank(product.getBarcode(), product.getName());
                    if (candidateKey != null && !uniqueProducts.containsKey(candidateKey)) {
                        uniqueProducts.put(candidateKey, product);
                    }
                }
            }
            catch (ResponseStatusException exception) {
                // Continue with the next query variant and rely on generic fallback if none succeed.
            }
        }

        return new ArrayList<>(uniqueProducts.values());
    }

    private List<String> buildSearchQueries(String searchQuery, String originalDescription) {
        Map<String, String> queries = new LinkedHashMap<>();
        addSearchQueryVariant(queries, searchQuery);
        addSearchQueryVariant(queries, reorderStapleQuery(searchQuery));

        List<String> tokens = meaningfulTokens(searchQuery);
        String stapleBaseQuery = buildStapleBaseQuery(tokens);
        addSearchQueryVariant(queries, stapleBaseQuery);

        String quantityAwareQuery = appendQuantityHint(stapleBaseQuery, originalDescription);
        addSearchQueryVariant(queries, quantityAwareQuery);

        addSearchQueryVariant(queries, firstNonBlank(originalDescription, null));
        return new ArrayList<>(queries.values());
    }

    private void addSearchQueryVariant(Map<String, String> queries, String query) {
        String sanitized = blankToNull(query);
        if (sanitized == null) {
            return;
        }
        queries.putIfAbsent(sanitized.toLowerCase(Locale.ROOT), sanitized);
    }

    private String reorderStapleQuery(String searchQuery) {
        String normalized = blankToNull(searchQuery);
        if (normalized == null) {
            return null;
        }

        return switch (normalized) {
            case "egg medium" -> "medium eggs";
            case "tea black" -> BLACK_TEA;
            case "tea green" -> GREEN_TEA;
            case "tea herbal" -> HERBAL_TEA;
            case "tea iced" -> ICED_TEA;
            case "tea milk" -> MILK_TEA;
            case "water mineral" -> MINERAL_WATER;
            case "water sparkling" -> SPARKLING_WATER;
            case "water still" -> STILL_WATER;
            case "bread whole" -> WHOLE_BREAD;
            case "bread wheat" -> WHEAT_BREAD;
            case "cheese cheddar" -> CHEDDAR_CHEESE;
            case "tomato cherry" -> CHERRY_TOMATO;
            case "spinach baby" -> BABY_SPINACH;
            case "rice basmati" -> BASMATI_RICE;
            case "juice apple" -> APPLE_JUICE;
            case "juice orange" -> ORANGE_JUICE;
            case "yogurt greek" -> GREEK_YOGURT;
            case "yogurt strawberry" -> STRAWBERRY_YOGURT;
            case "pasta penne" -> PENNE_PASTA;
            case "pasta spaghetti" -> SPAGHETTI_PASTA;
            default -> normalized;
        };
    }

    private String buildStapleBaseQuery(List<String> queryTokens) {
        List<String> stapleTokens = queryTokens.stream()
                .filter(STAPLE_SEARCH_TOKENS::contains)
                .toList();
        if (stapleTokens.isEmpty()) {
            return String.join(" ", queryTokens);
        }

        String joined = String.join(" ", stapleTokens);
        return reorderStapleQuery(joined);
    }

    private String appendQuantityHint(String searchQuery, String originalDescription) {
        String baseQuery = blankToNull(searchQuery);
        String quantity = extractPackageQuantity(originalDescription);
        if (baseQuery == null || quantity == null) {
            return baseQuery;
        }
        return baseQuery + " " + quantity;
    }

    private List<ReceiptProductCandidateDTO> enrichWithGenericFallback(
            String searchQuery,
            String originalDescription,
            List<ReceiptProductCandidateDTO> rankedCandidates) {
        ReceiptProductCandidateDTO genericFallback = buildGenericFallbackCandidate(searchQuery, originalDescription);
        if (genericFallback == null) {
            return rankedCandidates;
        }

        if (rankedCandidates.isEmpty()) {
            return List.of(genericFallback);
        }

        ReceiptProductCandidateDTO topCandidate = rankedCandidates.get(0);
        if (shouldPreferGenericFallback(searchQuery, topCandidate)) {
            List<ReceiptProductCandidateDTO> enriched = new ArrayList<>();
            enriched.add(genericFallback);
            enriched.addAll(rankedCandidates.stream()
                    .filter(candidate -> !sameCandidateLabel(candidate, genericFallback))
                    .limit(MAX_CANDIDATES - 1L)
                    .toList());
            return enriched;
        }

        return rankedCandidates;
    }

    private List<ReceiptProductCandidateDTO> rankCandidates(String searchQuery, String originalDescription, List<ProductDTO> products) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }

        Map<String, ReceiptProductCandidateDTO> uniqueCandidates = new LinkedHashMap<>();
        for (ProductDTO product : products) {
            if (product == null) {
                continue;
            }
            String candidateKey = firstNonBlank(product.getBarcode(), product.getName());
            if (candidateKey == null || uniqueCandidates.containsKey(candidateKey)) {
                continue;
            }
            double score = scoreProduct(searchQuery, originalDescription, product);
            ReceiptProductCandidateDTO candidate = buildCandidate(
                    product,
                    score,
                    confidenceForScore(score),
                    "openfoodfacts_search"
            );
            uniqueCandidates.put(candidateKey, candidate);
        }

        return uniqueCandidates.values().stream()
                .sorted(Comparator.comparing(ReceiptProductCandidateDTO::getScore).reversed())
                .limit(MAX_CANDIDATES)
                .toList();
    }

    private ReceiptProductCandidateDTO buildCandidate(
            ProductDTO product,
            double score,
            String confidence,
            String matchSource) {
        ReceiptProductCandidateDTO candidate = new ReceiptProductCandidateDTO();
        candidate.setProduct(product);
        candidate.setScore(roundScore(score));
        candidate.setConfidence(confidence);
        candidate.setMatchSource(matchSource);
        candidate.setSuggestedPantryItem(toPantrySuggestion(product));
        return candidate;
    }

    private ReceiptProductCandidateDTO buildGenericFallbackCandidate(String searchQuery, String originalDescription) {
        String canonicalQuery = firstNonBlank(reorderStapleQuery(searchQuery), searchQuery);
        List<String> queryTokens = meaningfulTokens(canonicalQuery);
        if (!isRecognizedGenericGrocery(queryTokens)) {
            return null;
        }

        ProductDTO genericProduct = new ProductDTO();
        genericProduct.setBarcode(buildGenericBarcode(queryTokens));
        genericProduct.setName(buildFriendlyProductName(queryTokens));
        genericProduct.setBrand("Receipt review");
        genericProduct.setQuantity(extractPackageQuantity(originalDescription));
        genericProduct.setCaloriesPerPackage(estimateGenericFallbackCalories(queryTokens, originalDescription));

        ReceiptPantryItemSuggestionDTO suggestion = new ReceiptPantryItemSuggestionDTO();
        suggestion.setBarcode(genericProduct.getBarcode());
        suggestion.setName(genericProduct.getName());
        suggestion.setKcalPerPackage(genericProduct.getCaloriesPerPackage());
        suggestion.setQuantity(1);
        suggestion.setPackageQuantity(genericProduct.getQuantity());
        suggestion.setNutriments(null);
        suggestion.setReadyForBulkAdd(true);

        ReceiptProductCandidateDTO candidate = new ReceiptProductCandidateDTO();
        candidate.setProduct(genericProduct);
        candidate.setScore(isProduceLike(queryTokens) ? 0.72 : 0.62);
        candidate.setConfidence(MEDIUM_CONFIDENCE);
        candidate.setMatchSource("generic_receipt_fallback");
        candidate.setSuggestedPantryItem(suggestion);
        return candidate;
    }

    private ReceiptPantryItemSuggestionDTO toPantrySuggestion(ProductDTO product) {
        ReceiptPantryItemSuggestionDTO suggestion = new ReceiptPantryItemSuggestionDTO();
        suggestion.setBarcode(blankToNull(product.getBarcode()));
        suggestion.setName(firstNonBlank(product.getName(), "Receipt item"));
        suggestion.setKcalPerPackage(product.getCaloriesPerPackage() != null ? product.getCaloriesPerPackage() : 0.0);
        suggestion.setQuantity(1);
        suggestion.setPackageQuantity(product.getQuantity());
        suggestion.setNutriments(product.getNutriments());
        suggestion.setReadyForBulkAdd(
                blankToNull(suggestion.getBarcode()) != null
                        && blankToNull(suggestion.getName()) != null
                        && suggestion.getQuantity() != null
                        && suggestion.getQuantity() > 0
                        && suggestion.getKcalPerPackage() != null
                        && suggestion.getKcalPerPackage() >= 0
        );
        return suggestion;
    }

    private List<ReceiptProductCandidateDTO> addBarcodeCandidateFirst(
            ReceiptProductCandidateDTO barcodeCandidate,
            List<ReceiptProductCandidateDTO> descriptionCandidates) {
        List<ReceiptProductCandidateDTO> candidates = new ArrayList<>();
        candidates.add(barcodeCandidate);
        for (ReceiptProductCandidateDTO candidate : descriptionCandidates) {
            if (!sameProduct(barcodeCandidate.getProduct(), candidate.getProduct())) {
                candidates.add(candidate);
            }
            if (candidates.size() >= MAX_CANDIDATES) {
                break;
            }
        }
        return candidates;
    }

    private boolean sameProduct(ProductDTO first, ProductDTO second) {
        if (first == null || second == null) {
            return false;
        }
        String firstBarcode = blankToNull(first.getBarcode());
        String secondBarcode = blankToNull(second.getBarcode());
        if (firstBarcode != null && firstBarcode.equals(secondBarcode)) {
            return true;
        }
        String firstName = blankToNull(first.getName());
        String secondName = blankToNull(second.getName());
        return firstName != null && firstName.equalsIgnoreCase(secondName);
    }

    private boolean sameCandidateLabel(ReceiptProductCandidateDTO first, ReceiptProductCandidateDTO second) {
        if (first == null || second == null) {
            return false;
        }
        return sameProduct(first.getProduct(), second.getProduct());
    }

    private double scoreProduct(String searchQuery, String originalDescription, ProductDTO product) {
        List<String> queryTokens = meaningfulTokens(searchQuery);
        List<String> productTokens = meaningfulTokens(productText(product));
        if (queryTokens.isEmpty() || productTokens.isEmpty()) {
            return 0.0;
        }

        double total = 0.0;
        int matchedTokenCount = 0;
        for (String queryToken : queryTokens) {
            double tokenScore = bestTokenScore(queryToken, productTokens);
            total += tokenScore;
            if (tokenScore >= 0.72) {
                matchedTokenCount++;
            }
        }
        if (matchedTokenCount == 0) {
            return 0.0;
        }

        double baseScore = total / queryTokens.size();
        double queryCoverage = (double) matchedTokenCount / queryTokens.size();
        double extraTokenPenalty = extraProductTokenPenalty(queryTokens, productTokens);
        double typeMismatchPenalty = productTypeMismatchPenalty(queryTokens, productTokens);
        double processedPenalty = processedFoodPenalty(queryTokens, productTokens);
        double stapleBoost = stapleSpecificBoost(queryTokens, product);
        double quantityBoost = packageQuantityBoost(product, extractPackageQuantity(originalDescription));

        double finalScore = baseScore * (0.65 + 0.35 * queryCoverage)
                + stapleBoost
                + quantityBoost
                - extraTokenPenalty
                - typeMismatchPenalty
                - processedPenalty;
        if (matchedTokenCount == 1 && queryTokens.size() > 1) {
            finalScore *= 0.58;
        }
        return Math.max(0.0, Math.min(1.0, finalScore));
    }

    private List<String> meaningfulTokens(String value) {
        return receiptAbbreviationService.tokenize(value).stream()
                .filter(token -> !receiptAbbreviationService.isLowValueToken(token))
                .toList();
    }

    private double extraProductTokenPenalty(List<String> queryTokens, List<String> productTokens) {
        int extraMeaningfulTokens = 0;
        for (String productToken : productTokens) {
            if (PRODUCT_TYPE_TOKENS.contains(productToken) && bestTokenScore(productToken, queryTokens) < 0.72) {
                extraMeaningfulTokens++;
            }
        }
        return Math.min(0.30, extraMeaningfulTokens * 0.10);
    }

    private double productTypeMismatchPenalty(List<String> queryTokens, List<String> productTokens) {
        for (String queryToken : queryTokens) {
            if (PRODUCT_TYPE_TOKENS.contains(queryToken) && bestTokenScore(queryToken, productTokens) >= 0.72) {
                return 0.0;
            }
        }

        for (String productToken : productTokens) {
            if (PRODUCT_TYPE_TOKENS.contains(productToken)) {
                return 0.14;
            }
        }
        return 0.0;
    }

    private double processedFoodPenalty(List<String> queryTokens, List<String> productTokens) {
        if (!isProduceLike(queryTokens)) {
            return 0.0;
        }

        int processedTokens = 0;
        for (String token : productTokens) {
            if (PROCESSED_PRODUCT_TOKENS.contains(token)) {
                processedTokens++;
            }
        }
        return Math.min(0.45, processedTokens * 0.15);
    }

    private double stapleSpecificBoost(List<String> queryTokens, ProductDTO product) {
        String joinedQuery = String.join(" ", queryTokens);
        String productText = productText(product).toLowerCase(Locale.ROOT);
        double boost = 0.0;

        if ("milk".equals(joinedQuery) && productText.contains("milk")) {
            boost += 0.18;
        }
        if ("egg medium".equals(joinedQuery) || "medium egg".equals(joinedQuery)) {
            if (productText.contains("egg")) {
                boost += 0.18;
            }
            if (productText.contains("medium")) {
                boost += 0.08;
            }
        }
        if ("rice basmati".equals(joinedQuery) && productText.contains("basmati") && productText.contains("rice")) {
            boost += 0.22;
        }
        if (APPLE_JUICE.equals(joinedQuery) && productText.contains("apple") && productText.contains("juice")) {
            boost += 0.22;
        }
        if (product.getCaloriesPerPackage() != null && product.getCaloriesPerPackage() > 0) {
            boost += 0.04;
        }
        return Math.min(0.28, boost);
    }

    private double packageQuantityBoost(ProductDTO product, String receiptQuantity) {
        String normalizedReceiptQuantity = normalizeQuantityHint(receiptQuantity);
        String normalizedProductQuantity = normalizeQuantityHint(product.getQuantity());
        if (normalizedReceiptQuantity == null || normalizedProductQuantity == null) {
            return 0.0;
        }
        return normalizedReceiptQuantity.equals(normalizedProductQuantity) ? 0.18 : 0.0;
    }

    private String normalizeQuantityHint(String quantity) {
        String value = blankToNull(quantity);
        if (value == null) {
            return null;
        }
        QuantityAmount quantityAmount = findQuantityAmount(value);
        if (quantityAmount == null) {
            return null;
        }
        String amount = normalizeAmountText(quantityAmount.amount());
        if (amount.endsWith(".0")) {
            amount = amount.substring(0, amount.length() - 2);
        }
        return amount + quantityAmount.unit();
    }

    private boolean shouldPreferGenericFallback(String searchQuery, ReceiptProductCandidateDTO topCandidate) {
        if (topCandidate == null) {
            return true;
        }

        List<String> queryTokens = meaningfulTokens(searchQuery);
        List<String> topTokens = meaningfulTokens(productText(topCandidate.getProduct()));
        double topScore = topCandidate.getScore() != null ? topCandidate.getScore() : 0.0;

        if (topScore < 0.55) {
            return true;
        }
        if (isProduceLike(queryTokens) && containsProcessedProductTokens(topTokens)) {
            return true;
        }
        return false;
    }

    private boolean isRecognizedGenericGrocery(List<String> queryTokens) {
        for (String token : queryTokens) {
            if (GENERIC_GROCERY_TOKENS.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean isProduceLike(List<String> queryTokens) {
        for (String token : queryTokens) {
            if (NON_PRODUCE_CONTEXT_TOKENS.contains(token)) {
                return false;
            }
        }
        for (String token : queryTokens) {
            if (FRESH_PRODUCE_TOKENS.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsProcessedProductTokens(List<String> productTokens) {
        for (String token : productTokens) {
            if (PROCESSED_PRODUCT_TOKENS.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String buildGenericBarcode(List<String> queryTokens) {
        return "receipt-generic:" + String.join("-", queryTokens);
    }

    private String buildFriendlyProductName(List<String> queryTokens) {
        String joined = String.join(" ", queryTokens);
        return switch (joined) {
            case APPLE_JUICE -> "Apple Juice";
            case "banana organic" -> "Banana Organic";
            case WHOLE_BREAD, "bread whole" -> "Whole Bread";
            case WHEAT_BREAD, "bread wheat" -> "Wheat Bread";
            case "cereal" -> "Cereal";
            case CHEDDAR_CHEESE -> "Cheddar Cheese";
            case "cheese feta" -> "Feta Cheese";
            case "cheese gouda" -> "Gouda Cheese";
            case "cheese parmesan" -> "Parmesan Cheese";
            case "coffee" -> "Coffee";
            case "coffee blnd" -> "Coffee Blend";
            case "egg medium" -> "Medium Eggs";
            case BLACK_TEA -> "Black Tea";
            case GREEN_TEA -> "Green Tea";
            case HERBAL_TEA -> "Herbal Tea";
            case ICED_TEA -> "Iced Tea";
            case "milk" -> "Milk";
            case MILK_TEA -> "Milk Tea";
            case "olive oil" -> "Olive Oil";
            case "oats" -> "Oats";
            case ORANGE_JUICE -> "Orange Juice";
            case "granola" -> "Granola";
            case "muesli" -> "Muesli";
            case "pasta penne" -> "Pasta Penne";
            case "pasta spaghetti" -> "Pasta Spaghetti";
            case "potato" -> "Potatoes";
            case BASMATI_RICE, "rice basmati" -> "Basmati Rice";
            case SPARKLING_WATER -> "Sparkling Water";
            case BABY_SPINACH, "spinach baby" -> "Baby Spinach";
            case STILL_WATER -> "Still Water";
            case CHERRY_TOMATO, "tomato cherry" -> "Cherry Tomatoes";
            case "tomato sauce" -> "Tomato Sauce";
            case "yogurt greek" -> "Greek Yogurt";
            case "yogurt strawberry" -> "Strawberry Yogurt";
            default -> toTitleCase(joined);
        };
    }

    private Double estimateGenericFallbackCalories(List<String> queryTokens, String originalDescription) {
        String joined = String.join(" ", queryTokens);
        String packageQuantity = extractPackageQuantity(originalDescription);

        return switch (joined) {
            case APPLE_JUICE -> estimateByQuantityOrDefault(packageQuantity, 46.0, Set.of("ml", "l"), 460.0);
            case "banana organic" -> 135.0;
            case WHOLE_BREAD, "bread whole" -> estimateByQuantityOrDefault(packageQuantity, 250.0, Set.of("g", "kg"), 1870.0);
            case WHEAT_BREAD, "bread wheat" -> estimateByQuantityOrDefault(packageQuantity, 245.0, Set.of("g", "kg"), 1225.0);
            case "cereal" -> estimateByQuantityOrDefault(packageQuantity, 380.0, Set.of("g", "kg"), 1900.0);
            case CHEDDAR_CHEESE -> estimateByQuantityOrDefault(packageQuantity, 400.0, Set.of("g", "kg"), 800.0);
            case "cheese feta" -> estimateByQuantityOrDefault(packageQuantity, 265.0, Set.of("g", "kg"), 530.0);
            case "cheese gouda" -> estimateByQuantityOrDefault(packageQuantity, 356.0, Set.of("g", "kg"), 712.0);
            case "cheese parmesan" -> estimateByQuantityOrDefault(packageQuantity, 430.0, Set.of("g", "kg"), 860.0);
            case "chicken breast" -> estimateByQuantityOrDefault(packageQuantity, 120.0, Set.of("g", "kg"), 600.0);
            case "coffee", "coffee blnd" -> estimateByQuantityOrDefault(packageQuantity, 2.0, Set.of("g", "kg"), 5.0);
            case "egg medium" -> estimateEggCalories(originalDescription, 700.0);
            case BLACK_TEA, GREEN_TEA, HERBAL_TEA -> estimateByQuantityOrDefault(packageQuantity, 1.0, Set.of("ml", "l"), 2.0);
            case "granola" -> estimateByQuantityOrDefault(packageQuantity, 450.0, Set.of("g", "kg"), 2250.0);
            case ICED_TEA -> estimateByQuantityOrDefault(packageQuantity, 30.0, Set.of("ml", "l"), 300.0);
            case "milk" -> estimateByQuantityOrDefault(packageQuantity, 64.0, Set.of("ml", "l"), 640.0);
            case MILK_TEA -> estimateByQuantityOrDefault(packageQuantity, 45.0, Set.of("ml", "l"), 450.0);
            case "muesli" -> estimateByQuantityOrDefault(packageQuantity, 380.0, Set.of("g", "kg"), 1900.0);
            case "oats" -> estimateByQuantityOrDefault(packageQuantity, 370.0, Set.of("g", "kg"), 1850.0);
            case "olive oil" -> estimateByQuantityOrDefault(packageQuantity, 820.0, Set.of("ml", "l"), 4100.0);
            case ORANGE_JUICE -> estimateByQuantityOrDefault(packageQuantity, 45.0, Set.of("ml", "l"), 450.0);
            case "pasta penne" -> estimateByQuantityOrDefault(packageQuantity, 360.0, Set.of("g", "kg"), 1800.0);
            case "pasta spaghetti" -> estimateByQuantityOrDefault(packageQuantity, 360.0, Set.of("g", "kg"), 1800.0);
            case BASMATI_RICE, "rice basmati" -> estimateByQuantityOrDefault(packageQuantity, 360.0, Set.of("g", "kg"), 3600.0);
            case SPARKLING_WATER, STILL_WATER, MINERAL_WATER -> 0.0;
            case "tomato sauce" -> estimateByQuantityOrDefault(packageQuantity, 35.0, Set.of("g", "kg", "ml", "l"), 175.0);
            case BABY_SPINACH, "spinach baby" -> estimateByQuantityOrDefault(packageQuantity, 23.0, Set.of("g", "kg"), 46.0);
            case CHERRY_TOMATO, "tomato cherry" -> estimateByQuantityOrDefault(packageQuantity, 18.0, Set.of("g", "kg"), 36.0);
            case "yogurt greek" -> estimateByQuantityOrDefault(packageQuantity, 95.0, Set.of("g", "kg"), 150.0);
            case "yogurt greek natural" -> estimateByQuantityOrDefault(packageQuantity, 100.0, Set.of("g", "kg"), 150.0);
            case "yogurt strawberry" -> estimateByQuantityOrDefault(packageQuantity, 110.0, Set.of("g", "kg"), 165.0);
            default -> estimateCategoryFallbackCalories(queryTokens, packageQuantity, originalDescription);
        };
    }

    private Double estimateCategoryFallbackCalories(
            List<String> queryTokens,
            String packageQuantity,
            String originalDescription) {
        if (containsAny(queryTokens, OIL_TOKENS)) {
            return estimateByQuantityOrDefault(packageQuantity, 820.0, Set.of("ml", "l", "g", "kg"), 4100.0);
        }
        if (containsAny(queryTokens, SAUCE_TOKENS)) {
            return estimateByQuantityOrDefault(packageQuantity, 35.0, Set.of("ml", "l", "g", "kg"), 175.0);
        }
        if (containsAny(queryTokens, DRINK_TOKENS)) {
            if (queryTokens.contains("water")) {
                return 0.0;
            }
            if (queryTokens.contains("tea")) {
                if (queryTokens.contains("milk")) {
                    return estimateByQuantityOrDefault(packageQuantity, 45.0, Set.of("ml", "l"), 450.0);
                }
                if (queryTokens.contains("iced")) {
                    return estimateByQuantityOrDefault(packageQuantity, 30.0, Set.of("ml", "l"), 300.0);
                }
                return estimateByQuantityOrDefault(packageQuantity, 1.0, Set.of("ml", "l"), 2.0);
            }
            if (queryTokens.contains("juice")) {
                if (queryTokens.contains("orange")) {
                    return estimateByQuantityOrDefault(packageQuantity, 45.0, Set.of("ml", "l"), 450.0);
                }
                return estimateByQuantityOrDefault(packageQuantity, 46.0, Set.of("ml", "l"), 460.0);
            }
            if (queryTokens.contains("milk")) {
                return estimateByQuantityOrDefault(packageQuantity, 64.0, Set.of("ml", "l"), 640.0);
            }
            return estimateByQuantityOrDefault(packageQuantity, 2.0, Set.of("g", "kg", "ml", "l"), 5.0);
        }
        if (queryTokens.contains("egg")) {
            return estimateEggCalories(originalDescription, 700.0);
        }
        if (containsAny(queryTokens, DAIRY_TOKENS)) {
            if (queryTokens.contains("cheddar")) {
                return estimateByQuantityOrDefault(packageQuantity, 400.0, Set.of("g", "kg"), 800.0);
            }
            if (queryTokens.contains("feta")) {
                return estimateByQuantityOrDefault(packageQuantity, 265.0, Set.of("g", "kg"), 530.0);
            }
            if (queryTokens.contains("gouda")) {
                return estimateByQuantityOrDefault(packageQuantity, 356.0, Set.of("g", "kg"), 712.0);
            }
            if (queryTokens.contains("parmesan")) {
                return estimateByQuantityOrDefault(packageQuantity, 430.0, Set.of("g", "kg"), 860.0);
            }
            if (queryTokens.contains("cheese")) {
                return estimateByQuantityOrDefault(packageQuantity, 330.0, Set.of("g", "kg"), 660.0);
            }
            if (queryTokens.contains("yogurt") || queryTokens.contains("yoghurt")) {
                if (queryTokens.contains("strawberry")) {
                    return estimateByQuantityOrDefault(packageQuantity, 110.0, Set.of("g", "kg"), 165.0);
                }
                return estimateByQuantityOrDefault(packageQuantity, 95.0, Set.of("g", "kg"), 150.0);
            }
            return estimateByQuantityOrDefault(packageQuantity, 120.0, Set.of("g", "kg", "ml", "l"), 240.0);
        }
        if (containsAny(queryTokens, GRAIN_TOKENS)) {
            if (queryTokens.contains("bread")) {
                return estimateByQuantityOrDefault(packageQuantity, 250.0, Set.of("g", "kg"), 1250.0);
            }
            if (queryTokens.contains("granola")) {
                return estimateByQuantityOrDefault(packageQuantity, 450.0, Set.of("g", "kg"), 2250.0);
            }
            if (queryTokens.contains("muesli")) {
                return estimateByQuantityOrDefault(packageQuantity, 380.0, Set.of("g", "kg"), 1900.0);
            }
            if (queryTokens.contains("oats")) {
                return estimateByQuantityOrDefault(packageQuantity, 370.0, Set.of("g", "kg"), 1850.0);
            }
            return estimateByQuantityOrDefault(packageQuantity, 360.0, Set.of("g", "kg"), 1800.0);
        }
        if (containsAny(queryTokens, FRUIT_TOKENS)) {
            if (queryTokens.contains("avocado")) {
                return estimateByQuantityOrDefault(packageQuantity, 160.0, Set.of("g", "kg"), 240.0);
            }
            if (queryTokens.contains("banana")) {
                return estimateByQuantityOrDefault(packageQuantity, 89.0, Set.of("g", "kg"), 135.0);
            }
            if (queryTokens.contains("apple")) {
                return estimateByQuantityOrDefault(packageQuantity, 52.0, Set.of("g", "kg"), 95.0);
            }
            if (queryTokens.contains("orange")) {
                return estimateByQuantityOrDefault(packageQuantity, 47.0, Set.of("g", "kg"), 85.0);
            }
            if (queryTokens.contains("strawberry")) {
                return estimateByQuantityOrDefault(packageQuantity, 32.0, Set.of("g", "kg"), 50.0);
            }
            if (queryTokens.contains("grape")) {
                return estimateByQuantityOrDefault(packageQuantity, 69.0, Set.of("g", "kg"), 105.0);
            }
            return estimateByQuantityOrDefault(packageQuantity, 55.0, Set.of("g", "kg"), 135.0);
        }
        if (containsAny(queryTokens, VEGETABLE_TOKENS)) {
            if (queryTokens.contains("potato")) {
                return estimateByQuantityOrDefault(packageQuantity, 77.0, Set.of("g", "kg"), 1540.0);
            }
            if (queryTokens.contains("tomato")) {
                return estimateByQuantityOrDefault(packageQuantity, 18.0, Set.of("g", "kg"), 36.0);
            }
            if (queryTokens.contains("spinach")) {
                return estimateByQuantityOrDefault(packageQuantity, 23.0, Set.of("g", "kg"), 46.0);
            }
            if (queryTokens.contains("cucumber")) {
                return estimateByQuantityOrDefault(packageQuantity, 15.0, Set.of("g", "kg"), 50.0);
            }
            return estimateByQuantityOrDefault(packageQuantity, 25.0, Set.of("g", "kg"), 50.0);
        }
        return 0.0;
    }

    private boolean containsAny(List<String> tokens, Set<String> categoryTokens) {
        for (String token : tokens) {
            if (categoryTokens.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private Double estimateByQuantityOrDefault(
            String packageQuantity,
            double kcalPer100Units,
            Set<String> supportedUnits,
            double defaultCalories) {
        QuantityAmount parsed = parsePackageQuantity(packageQuantity);
        if (parsed == null || !supportedUnits.contains(parsed.unit())) {
            return defaultCalories;
        }

        double amountInBaseUnits = switch (parsed.unit()) {
            case "kg" -> parsed.amount() * 1000.0;
            case "l" -> parsed.amount() * 1000.0;
            default -> parsed.amount();
        };
        return roundScore((kcalPer100Units * amountInBaseUnits) / 100.0);
    }

    private Double estimateEggCalories(String originalDescription, double defaultCalories) {
        String value = blankToNull(originalDescription);
        if (value == null) {
            return defaultCalories;
        }

        Integer count = extractCountQuantity(value);
        if (count == null) {
            return defaultCalories;
        }
        return roundScore(count * 70.0);
    }

    private QuantityAmount parsePackageQuantity(String packageQuantity) {
        String value = blankToNull(packageQuantity);
        if (value == null) {
            return null;
        }

        return findQuantityAmount(value);
    }

    private String toTitleCase(String value) {
        List<String> words = splitWords(value);
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                builder.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.toString();
    }

    private Integer extractCountQuantity(String value) {
        List<String> words = splitWords(value);
        for (int i = 0; i < words.size() - 1; i++) {
            Integer amount = parseInteger(words.get(i));
            if (amount != null && isCountUnit(words.get(i + 1))) {
                return amount;
            }
        }
        return null;
    }

    private List<String> splitWords(String value) {
        List<String> words = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (Character.isWhitespace(character)) {
                if (current.length() > 0) {
                    words.add(current.toString());
                    current.setLength(0);
                }
            }
            else {
                current.append(character);
            }
        }
        if (current.length() > 0) {
            words.add(current.toString());
        }
        return words;
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return null;
            }
        }
        try {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean isCountUnit(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.equals("pcs") || normalized.equals("pc") || normalized.equals("ct");
    }

    private String extractPackageQuantity(String originalDescription) {
        String value = blankToNull(originalDescription);
        if (value == null) {
            return null;
        }

        QuantityAmount quantityAmount = findQuantityAmount(value);
        if (quantityAmount == null) {
            return null;
        }
        return normalizeAmountText(quantityAmount.amount()) + quantityAmount.unit();
    }

    private QuantityAmount findQuantityAmount(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!Character.isDigit(character)) {
                continue;
            }

            int amountEnd = consumeAmount(value, index);
            if (amountEnd == index) {
                continue;
            }

            int unitStart = skipWhitespace(value, amountEnd);
            int unitEnd = consumeLetters(value, unitStart);
            if (unitEnd == unitStart) {
                continue;
            }

            String unit = value.substring(unitStart, unitEnd).toLowerCase(Locale.ROOT);
            if (!PACKAGE_QUANTITY_UNITS.contains(unit)) {
                continue;
            }

            Double amount = parseDoubleValue(value.substring(index, amountEnd));
            if (amount == null) {
                continue;
            }
            return new QuantityAmount(amount, unit);
        }
        return null;
    }

    private int consumeAmount(String value, int startIndex) {
        boolean seenSeparator = false;
        int index = startIndex;
        while (index < value.length()) {
            char character = value.charAt(index);
            if (Character.isDigit(character)) {
                index++;
                continue;
            }
            if ((character == '.' || character == ',') && !seenSeparator) {
                seenSeparator = true;
                index++;
                continue;
            }
            break;
        }
        return index;
    }

    private int skipWhitespace(String value, int startIndex) {
        int index = startIndex;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private int consumeLetters(String value, int startIndex) {
        int index = startIndex;
        while (index < value.length() && Character.isLetter(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private Double parseDoubleValue(String value) {
        try {
            return Double.parseDouble(value.replace(',', '.'));
        }
        catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalizeAmountText(double amount) {
        String amountText = Double.toString(amount);
        if (amountText.endsWith(".0")) {
            return amountText.substring(0, amountText.length() - 2);
        }
        return amountText;
    }

    private double bestTokenScore(String queryToken, List<String> productTokens) {
        double bestScore = 0.0;
        for (String productToken : productTokens) {
            if (queryToken.equals(productToken)) {
                return 1.0;
            }
            if (productToken.startsWith(queryToken) || queryToken.startsWith(productToken)) {
                bestScore = Math.max(bestScore, 0.82);
            }
            bestScore = Math.max(bestScore, editDistanceSimilarity(queryToken, productToken));
        }
        return bestScore;
    }

    private double editDistanceSimilarity(String first, String second) {
        if (first.length() < 4 || second.length() < 4) {
            return 0.0;
        }

        int distance = levenshteinDistance(first, second);
        int maxLength = Math.max(first.length(), second.length());
        double similarity = 1.0 - ((double) distance / maxLength);
        return similarity >= 0.72 ? similarity : 0.0;
    }

    private int levenshteinDistance(String first, String second) {
        int[] previous = new int[second.length() + 1];
        int[] current = new int[second.length() + 1];
        for (int j = 0; j <= second.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= first.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= second.length(); j++) {
                int cost = first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[second.length()];
    }

    private String productText(ProductDTO product) {
        return String.join(" ",
                firstNonBlank(product.getName(), ""),
                firstNonBlank(product.getBrand(), ""),
                firstNonBlank(product.getQuantity(), "")
        );
    }

    private String confidenceForScore(double score) {
        if (score >= HIGH_CONFIDENCE_THRESHOLD) {
            return HIGH_CONFIDENCE;
        }
        if (score >= MEDIUM_CONFIDENCE_THRESHOLD) {
            return MEDIUM_CONFIDENCE;
        }
        return LOW_CONFIDENCE;
    }

    private double roundScore(double score) {
        return Math.round(score * 100.0) / 100.0;
    }

    private record QuantityAmount(double amount, String unit) {
    }

    private String firstNonBlank(String first, String second) {
        String firstValue = blankToNull(first);
        return firstValue != null ? firstValue : blankToNull(second);
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
