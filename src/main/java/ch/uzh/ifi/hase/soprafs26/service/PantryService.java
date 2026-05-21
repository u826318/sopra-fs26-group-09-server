package ch.uzh.ifi.hase.soprafs26.service;

import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.entity.ConsumptionLog;
import ch.uzh.ifi.hase.soprafs26.entity.Household;
import ch.uzh.ifi.hase.soprafs26.entity.HouseholdMemberId;
import ch.uzh.ifi.hase.soprafs26.entity.PantryItem;
import ch.uzh.ifi.hase.soprafs26.entity.PantryItemMicronutrients;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.ConsumptionLogRepository;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdMemberRepository;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdRepository;
import ch.uzh.ifi.hase.soprafs26.repository.PantryItemRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.PantryItemPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.PortionEstimateResponseDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductDTO;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetLookupService;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetProductMapper;
import ch.uzh.ifi.hase.soprafs26.websocket.PantryUpdateMessage;


@Service
@Transactional
public class PantryService {

    /**
     * Upper bound for {@link #bulkAddItems(Long, List, Long)} to avoid oversized payloads.
     */
    public static final int MAX_ITEMS_PER_BULK_REQUEST = 100;

    // Issue #114 — allowed values for amountUnit
    private static final Set<String> VALID_AMOUNT_UNITS = Set.of("g", "ml", "package", "serving");
    private static final Set<String> VALID_CONSUMPTION_UNITS = Set.of("g", "ml", "package", "serving");

    private final PantryItemRepository pantryItemRepository;
    private final ConsumptionLogRepository consumptionLogRepository;
    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final UserRepository userRepository;
    private final PantryBroadcastService pantryBroadcastService;
    private final PantryItemMicronutrientService pantryItemMicronutrientService;
    private final DailyNutrientIntakeService dailyNutrientIntakeService;
    private final MealPortionEstimateService mealPortionEstimateService;
    private final LocalDatasetLookupService localDatasetLookupService;
    private final LocalDatasetProductMapper localDatasetProductMapper;

    public PantryService(
            PantryItemRepository pantryItemRepository,
            ConsumptionLogRepository consumptionLogRepository,
            HouseholdRepository householdRepository,
            HouseholdMemberRepository householdMemberRepository,
            UserRepository userRepository,
            PantryBroadcastService pantryBroadcastService,
            PantryItemMicronutrientService pantryItemMicronutrientService,
            DailyNutrientIntakeService dailyNutrientIntakeService,
            MealPortionEstimateService mealPortionEstimateService,
            LocalDatasetLookupService localDatasetLookupService,
            LocalDatasetProductMapper localDatasetProductMapper
    ) {
        this.pantryItemRepository = pantryItemRepository;
        this.consumptionLogRepository = consumptionLogRepository;
        this.householdRepository = householdRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.userRepository = userRepository;
        this.pantryBroadcastService = pantryBroadcastService;
        this.pantryItemMicronutrientService = pantryItemMicronutrientService;
        this.dailyNutrientIntakeService = dailyNutrientIntakeService;
        this.mealPortionEstimateService = mealPortionEstimateService;
        this.localDatasetLookupService = localDatasetLookupService;
        this.localDatasetProductMapper = localDatasetProductMapper;
    }

    // Issue #114/#consume-units — unit-aware calorie calculation for a single consume operation.
    private Double computeConsumedCalories(PantryItem item, String consumedUnit, double consumedAmount) {
        String unit = normalizeConsumptionUnit(consumedUnit, item);
        if ("package".equals(unit) && item.getKcalPerPackage() != null && item.getKcalPerPackage() > 0) {
            return item.getKcalPerPackage() * consumedAmount;
        }
        if ("serving".equals(unit) && item.getKcalPerServing() != null && item.getKcalPerServing() > 0) {
            return item.getKcalPerServing() * consumedAmount;
        }

        BigDecimal consumedBasisAmount = resolveConsumedBasisAmount(item, unit, BigDecimal.valueOf(consumedAmount));
        if (consumedBasisAmount == null || item.getMicronutrients() == null) {
            // Fallback for g/ml items that have kcal fields but no micronutrients stored
            if ("g".equals(unit) && item.getKcalPer100g() != null && item.getKcalPer100g() > 0) {
                return item.getKcalPer100g() * consumedAmount / 100.0;
            }
            if ("ml".equals(unit) && item.getKcalPer100ml() != null && item.getKcalPer100ml() > 0) {
                return item.getKcalPer100ml() * consumedAmount / 100.0;
            }
            return null;
        }

        String basisUnit = item.getMicronutrients().getNutritionBasisUnit();
        BigDecimal basisAmount = item.getMicronutrients().getNutritionBasisAmount();
        if (basisAmount == null || basisAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        if ("g".equals(basisUnit) && item.getKcalPer100g() != null && item.getKcalPer100g() > 0) {
            return item.getKcalPer100g() * consumedBasisAmount.doubleValue() / basisAmount.doubleValue();
        }
        if ("ml".equals(basisUnit) && item.getKcalPer100ml() != null && item.getKcalPer100ml() > 0) {
            return item.getKcalPer100ml() * consumedBasisAmount.doubleValue() / basisAmount.doubleValue();
        }
        return null;
    }

    /**
     * Calculates the total calories currently stored in the pantry for one household.
     * Issue #114 — formula depends on amountUnit:
     *   g       → kcalPer100g * amount / 100
     *   ml      → kcalPer100ml * amount / 100
     *   package → kcalPerPackage * amount
     */
    public double calculateTotalCalories(Long householdId) {
        List<PantryItem> pantryItems = pantryItemRepository.findByHouseholdId(householdId);

        double totalCalories = 0.0;
        for (PantryItem item : pantryItems) {
            String unit = item.getAmountUnit();
            Double amount = item.getAmount();
            if (unit == null || amount == null) {
                continue;
            }
            if ("g".equals(unit) && item.getKcalPer100g() != null) {
                totalCalories += item.getKcalPer100g() * amount / 100.0;
            }
            else if ("ml".equals(unit) && item.getKcalPer100ml() != null) {
                totalCalories += item.getKcalPer100ml() * amount / 100.0;
            }
            else if ("package".equals(unit) && item.getKcalPerPackage() != null) {
                totalCalories += item.getKcalPerPackage() * amount;
            }
            else if ("serving".equals(unit) && item.getKcalPerServing() != null) {
                totalCalories += item.getKcalPerServing() * amount;
            }
        }

        return totalCalories;
    }

    public List<PantryItem> getPantryItems(Long householdId, Long authenticatedUserId) {
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found."));

        HouseholdMemberId membershipId = new HouseholdMemberId(authenticatedUserId, household.getId());
        boolean isMember = householdMemberRepository.existsById(membershipId);
        if (!isMember) {
            throw new IllegalArgumentException("User is not a member of this household.");
        }

        return pantryItemRepository.findByHouseholdId(householdId);
    }

    public PantryItem addItem(Long householdId, PantryItemPostDTO pantryItemPostDTO, Long authenticatedUserId) {
        validatePantryItemPayload(pantryItemPostDTO);

        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found."));

        HouseholdMemberId membershipId = new HouseholdMemberId(authenticatedUserId, household.getId());
        boolean isMember = householdMemberRepository.existsById(membershipId);
        if (!isMember) {
            throw new IllegalArgumentException("User is not a member of this household.");
        }

        PantryItem saved = persistIncomingPantryItem(householdId, pantryItemPostDTO);

        broadcastItemAdded(householdId, saved, authenticatedUserId);

        return saved;
    }

    /**
     * Adds multiple pantry lines in one request. Validated up-front; all persistence happens in this transaction.
     * Each persisted row triggers the same {@code ITEM_ADDED} WebSocket broadcast as {@link #addItem}.
     */
    public List<PantryItem> bulkAddItems(Long householdId, List<PantryItemPostDTO> items,
            Long authenticatedUserId) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Bulk add payload must contain at least one item.");
        }
        if (items.size() > MAX_ITEMS_PER_BULK_REQUEST) {
            throw new IllegalArgumentException(
                    "Cannot add more than " + MAX_ITEMS_PER_BULK_REQUEST + " items in one request.");
        }

        for (PantryItemPostDTO dto : items) {
            if (dto == null) {
                throw new IllegalArgumentException("Bulk add items must not contain null entries.");
            }
            validatePantryItemPayload(dto);
        }

        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found."));

        HouseholdMemberId membershipId = new HouseholdMemberId(authenticatedUserId, household.getId());
        boolean isMember = householdMemberRepository.existsById(membershipId);
        if (!isMember) {
            throw new IllegalArgumentException("User is not a member of this household.");
        }

        List<PantryItem> savedItems = new ArrayList<>(items.size());
        for (PantryItemPostDTO dto : items) {
            PantryItem saved = persistIncomingPantryItem(householdId, dto);
            savedItems.add(saved);
            broadcastItemAdded(householdId, saved, authenticatedUserId);
        }

        return savedItems;
    }

    private void validatePantryItemPayload(PantryItemPostDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Pantry item payload must not be empty.");
        }
        // Issue #114 — validate amount and unit instead of integer quantity
        if (dto.getAmount() == null || dto.getAmount() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        if (dto.getAmountUnit() == null || !VALID_AMOUNT_UNITS.contains(dto.getAmountUnit())) {
            throw new IllegalArgumentException("Amount unit must be one of: g, ml, package, serving.");
        }
        if (isBlank(dto.getBarcode()) && isBlank(dto.getName())) {
            throw new IllegalArgumentException("Product name or barcode must not be empty.");
        }
    }

    private PantryItem persistIncomingPantryItem(Long householdId, PantryItemPostDTO dto) {
        String normalizedBarcode = dto.getBarcode() != null ? dto.getBarcode().trim() : null;
        LocalDatasetProductDTO localProduct = lookupLocalProductByBarcodeIfPresent(normalizedBarcode);
        boolean useLocalDatasetProduct = localProduct != null && !Boolean.TRUE.equals(dto.getManualEntry());

        String persistedBarcode = useLocalDatasetProduct
                ? cleanOrFallback(localProduct.getBarcode(), normalizedBarcode)
                : normalizeBarcode(normalizedBarcode);
        String persistedName = useLocalDatasetProduct
                ? cleanOrFallback(localProduct.getName(), normalizedBarcode)
                : cleanOrFallback(dto.getName(), normalizedBarcode);

        PantryItem saved = mergeOrCreatePantryItem(
                householdId,
                persistedBarcode,
                persistedName,
                dto.getAmountUnit(),
                dto.getAmount(),
                useLocalDatasetProduct ? calculateKcalPerPackage(localProduct) : positiveOrNull(dto.getKcalPerPackage()),
                useLocalDatasetProduct ? calculateKcalPer100g(localProduct) : positiveOrNull(dto.getKcalPer100g()),
                useLocalDatasetProduct ? calculateKcalPer100ml(localProduct) : positiveOrNull(dto.getKcalPer100ml()),
                dto.getExpirationDate());

        if (useLocalDatasetProduct) {
            pantryItemMicronutrientService.upsertMicronutrientsPerBasisFromLocalDataset(saved, localProduct);
        }
        else {
            pantryItemMicronutrientService.upsertManualMicronutrientsPerBasis(
                    saved,
                    dto.getAmountUnit(),
                    dto.getMicronutrients());
        }

        return saved;
    }

    private LocalDatasetProductDTO lookupLocalProductByBarcodeIfPresent(String barcode) {
        if (isBlank(barcode)) {
            return null;
        }

        return localDatasetLookupService.findRawRowByBarcode(barcode.trim())
                .map(localDatasetProductMapper::toDto)
                .orElse(null);
    }

    private Double positiveOrNull(Double value) {
        return value != null && value > 0 ? value : null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Double calculateKcalPerPackage(LocalDatasetProductDTO product) {
        if (product == null
                || product.getNutrition() == null
                || product.getNutrition().getCoreNutrition() == null) {
            return null;
        }

        LocalDatasetProductDTO.NutrientAmountDTO energy =
                product.getNutrition().getCoreNutrition().get("energy-kcal");

        if (energy == null || energy.getValue() == null) {
            return null;
        }

        String basisUnit = product.getNutrition().getBasisUnit();
        String packageUnit = product.getPackageQuantityUnit();
        Double packageQuantity = product.getPackageQuantity();

        if (basisUnit == null
                || packageUnit == null
                || packageQuantity == null
                || packageQuantity <= 0
                || !basisUnit.equals(packageUnit)) {
            return null;
        }

        return energy.getValue() * packageQuantity / 100.0;
    }


    private Double calculateKcalPer100g(LocalDatasetProductDTO product) {
        return calculateKcalPerBasisUnit(product, "g");
    }

    private Double calculateKcalPer100ml(LocalDatasetProductDTO product) {
        return calculateKcalPerBasisUnit(product, "ml");
    }

    private Double calculateKcalPerBasisUnit(LocalDatasetProductDTO product, String expectedBasisUnit) {
        if (product == null
                || product.getNutrition() == null
                || product.getNutrition().getCoreNutrition() == null
                || product.getNutrition().getBasisAmount() == null
                || product.getNutrition().getBasisAmount() <= 0
                || product.getNutrition().getBasisUnit() == null
                || !expectedBasisUnit.equalsIgnoreCase(product.getNutrition().getBasisUnit())) {
            return null;
        }

        LocalDatasetProductDTO.NutrientAmountDTO energy =
                product.getNutrition().getCoreNutrition().get("energy-kcal");
        if (energy == null || energy.getValue() == null) {
            return null;
        }

        return energy.getValue() * 100.0 / product.getNutrition().getBasisAmount();
    }

    private String cleanOrFallback(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }

        return value.trim();
    }

    private Double firstNonNull(Double primary, Double fallback) {
        return primary != null ? primary : fallback;
    }

    private Integer derivePackageQuantityIfWhole(PantryItemPostDTO dto) {
        if (dto == null || dto.getAmount() == null || dto.getAmountUnit() == null) {
            return null;
        }
        if (!"package".equalsIgnoreCase(dto.getAmountUnit().trim())) {
            return null;
        }

        double amount = dto.getAmount();
        if (amount <= 0 || amount > Integer.MAX_VALUE || amount % 1 != 0) {
            return null;
        }

        return (int) amount;
    }

    private void broadcastItemAdded(Long householdId, PantryItem saved, Long authenticatedUserId) {
        User actor = userRepository.findById(authenticatedUserId).orElse(null);
        PantryUpdateMessage msg = new PantryUpdateMessage();
        msg.setEventType("ITEM_ADDED");
        msg.setHouseholdId(householdId);
        msg.setTriggeredByUserId(authenticatedUserId);
        msg.setTriggeredByUsername(actor != null ? actor.getUsername() : null);
        msg.setTimestamp(Instant.now().toString());
        msg.setNewTotalCalories(calculateTotalCalories(householdId));
        PantryUpdateMessage.PantryItemPayload payload = new PantryUpdateMessage.PantryItemPayload();
        payload.setItemId(saved.getId());
        payload.setProductName(saved.getName());
        payload.setBarcode(saved.getBarcode());
        // Issue #114 — broadcast the stored amount and its unit
        payload.setAmount(saved.getAmount());
        payload.setAmountUnit(saved.getAmountUnit());
        payload.setCaloriesPerUnit(saved.getKcalPerPackage());
        payload.setAddedByUserId(authenticatedUserId);
        payload.setAddedAt(saved.getAddedAt().toString());
        msg.setItem(payload);
        pantryBroadcastService.broadcastPantryUpdate(householdId, msg);
    }

    // Issue #114 — merge only when barcode AND amountUnit match; otherwise create a new row
    private PantryItem mergeOrCreatePantryItem(
            Long householdId,
            String barcode,
            String name,
            String amountUnit,
            Double amount,
            Double kcalPerPackage,
            Double kcalPer100g,
            Double kcalPer100ml,
            Double kcalPerServing,
            java.time.LocalDate expirationDate
    ) {
        String normalizedBarcode = normalizeBarcode(barcode);

        List<PantryItem> matchingItems = (normalizedBarcode == null)
                ? List.of()
                : pantryItemRepository.findByHouseholdIdAndBarcode(householdId, normalizedBarcode);

        PantryItem matchingItem = matchingItems.stream()
                .filter(item -> amountUnit.equals(item.getAmountUnit()))
                .findFirst()
                .orElse(null);

        if (matchingItem != null) {
            matchingItem.setName(name);
            matchingItem.setKcalPerPackage(kcalPerPackage);
            matchingItem.setKcalPer100g(kcalPer100g);
            matchingItem.setKcalPer100ml(kcalPer100ml);
            matchingItem.setKcalPerServing(kcalPerServing);
            matchingItem.setAmount(safeAmount(matchingItem.getAmount()) + safeAmount(amount));
            if (expirationDate != null) {
                matchingItem.setExpirationDate(expirationDate);
            }
            return pantryItemRepository.save(matchingItem);
        }

        PantryItem pantryItem = new PantryItem();
        pantryItem.setHouseholdId(householdId);
        pantryItem.setBarcode(normalizedBarcode);
        pantryItem.setName(name);
        pantryItem.setAmountUnit(amountUnit);
        pantryItem.setAmount(safeAmount(amount));
        pantryItem.setInitialAmount(safeAmount(amount));
        pantryItem.setKcalPerPackage(kcalPerPackage);
        pantryItem.setKcalPer100g(kcalPer100g);
        pantryItem.setKcalPer100ml(kcalPer100ml);
        pantryItem.setKcalPerServing(kcalPerServing);
        pantryItem.setExpirationDate(expirationDate);
        pantryItem.setAddedAt(Instant.now());

        return pantryItemRepository.save(pantryItem);
    }

    private double safeAmount(Double amount) {
        return amount == null ? 0.0 : amount;
    }

    private String normalizeBarcode(String barcode) {
        if (barcode == null) {
            return null;
        }

        String trimmedBarcode = barcode.trim();
        return trimmedBarcode.isEmpty() ? null : trimmedBarcode;
    }

    public PortionEstimateResponseDTO estimateMealPortion(
            Long householdId,
            Long itemId,
            MultipartFile image,
            Long authenticatedUserId) {

        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found."));

        HouseholdMemberId membershipId = new HouseholdMemberId(authenticatedUserId, household.getId());
        boolean isMember = householdMemberRepository.existsById(membershipId);
        if (!isMember) {
            throw new IllegalArgumentException("User is not a member of this household.");
        }

        PantryItem pantryItem = pantryItemRepository.findByIdAndHouseholdId(itemId, householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pantry item not found in this household."));

        return mealPortionEstimateService.estimatePortion(pantryItem, image);
    }

    public ConsumeResult consumeItem(Long householdId, Long itemId, Double amount, Long authenticatedUserId) {
        return consumeItem(householdId, itemId, amount, null, null, false, authenticatedUserId, null);
    }

    public ConsumeResult consumeItem(
            Long householdId,
            Long itemId,
            Double amount,
            Double kcalPerPackageOverride,
            boolean skipCalorieLogging,
            Long authenticatedUserId) {
        return consumeItem(householdId, itemId, amount, null, kcalPerPackageOverride, skipCalorieLogging, authenticatedUserId, null);
    }

    public ConsumeResult consumeItem(
            Long householdId,
            Long itemId,
            Double amount,
            String amountUnit,
            Double kcalPerPackageOverride,
            boolean skipCalorieLogging,
            Long authenticatedUserId,
            Long consumedForUserId) {  // Issue #121 — null means self
        // Issue #133 — amount (Double) replaces quantity (Integer) to support portion-based consumption
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
        if (kcalPerPackageOverride != null && kcalPerPackageOverride < 0) {
            throw new IllegalArgumentException("Calories per package must not be negative.");
        }

        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found."));

        HouseholdMemberId membershipId = new HouseholdMemberId(authenticatedUserId, household.getId());
        boolean isMember = householdMemberRepository.existsById(membershipId);
        if (!isMember) {
            throw new IllegalArgumentException("User is not a member of this household.");
        }

        // Issue #121 — if a target consumer is specified, validate they are also a member
        Long effectiveUserId = authenticatedUserId;
        if (consumedForUserId != null) {
            HouseholdMemberId targetMemberId = new HouseholdMemberId(consumedForUserId, household.getId());
            if (!householdMemberRepository.existsById(targetMemberId)) {
                throw new IllegalArgumentException("Target user is not a member of this household.");
            }
            effectiveUserId = consumedForUserId;
        }

        PantryItem pantryItem = pantryItemRepository.findByIdAndHouseholdId(itemId, householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pantry item not found in this household."));

        double consumeAmount = amount;
        String consumedUnit = normalizeConsumptionUnit(amountUnit, pantryItem);
        if (!VALID_CONSUMPTION_UNITS.contains(consumedUnit)) {
            throw new IllegalArgumentException("Amount unit must be one of: g, ml, serving, package.");
        }

        BigDecimal inventoryAmountToSubtract = resolveInventoryAmountToSubtract(
                pantryItem, consumedUnit, BigDecimal.valueOf(consumeAmount));
        if (inventoryAmountToSubtract != null
                && inventoryAmountToSubtract.doubleValue() > safeAmount(pantryItem.getAmount())) {
            throw new IllegalArgumentException("Consumed quantity exceeds available quantity.");
        }

        if (!skipCalorieLogging && kcalPerPackageOverride != null) {
            pantryItem.setKcalPerPackage(kcalPerPackageOverride);
            pantryItemRepository.save(pantryItem);
        }

        Double consumedCalories = skipCalorieLogging ? null : computeConsumedCalories(pantryItem, consumedUnit, consumeAmount);

        // Issue #133 — log consumed amount rounded to nearest int for display in activity feed
        int loggedQuantity = (int) Math.max(1, Math.round(consumeAmount));
        ConsumptionLog log = new ConsumptionLog();
        log.setHouseholdId(householdId);
        log.setUserId(effectiveUserId);           // Issue #121 — attributed consumer
        log.setActorUserId(authenticatedUserId);  // Issue #121 — who clicked consume
        log.setPantryItemId(pantryItem.getId());
        log.setProductNameSnapshot(pantryItem.getName());
        log.setConsumedQuantity(loggedQuantity);
        log.setConsumedUnit(consumedUnit);
        log.setConsumedCalories(consumedCalories);
        log.setConsumedAt(Instant.now());
        consumptionLogRepository.save(log);

        BigDecimal nutrientMultiplier = resolveNutritionBasisMultiplier(
                pantryItem, consumedUnit, BigDecimal.valueOf(consumeAmount));
        if (!skipCalorieLogging && nutrientMultiplier != null) {
            dailyNutrientIntakeService.recordConsumedPantryItem(
                    effectiveUserId,
                    pantryItem,
                    nutrientMultiplier,
                    log.getConsumedAt());
        }

        ConsumeResult result = new ConsumeResult();
        result.setItemId(pantryItem.getId());
        result.setConsumedCalories(consumedCalories);

        double remainingAmount = safeAmount(pantryItem.getAmount());
        if (inventoryAmountToSubtract != null) {
            remainingAmount = safeAmount(pantryItem.getAmount()) - inventoryAmountToSubtract.doubleValue();
        }

        if (remainingAmount <= 0) {
            pantryItemRepository.delete(pantryItem);
            result.setRemainingAmount(0.0);
            result.setRemoved(true);
        }
        else {
            if (inventoryAmountToSubtract != null) {
                pantryItem.setAmount(remainingAmount);
                pantryItemRepository.save(pantryItem);
            }
            result.setRemainingAmount(remainingAmount);
            result.setRemoved(false);
        }

        User actor = userRepository.findById(authenticatedUserId).orElse(null);
        PantryUpdateMessage msg = new PantryUpdateMessage();
        msg.setEventType("ITEM_CONSUMED");
        msg.setHouseholdId(householdId);
        msg.setTriggeredByUserId(authenticatedUserId);
        msg.setTriggeredByUsername(actor != null ? actor.getUsername() : null);
        msg.setTimestamp(Instant.now().toString());
        msg.setNewTotalCalories(calculateTotalCalories(householdId));
        pantryBroadcastService.broadcastPantryUpdate(householdId, msg);

        return result;
    }


    private String normalizeConsumptionUnit(String requestedUnit, PantryItem item) {
        String unit = requestedUnit == null || requestedUnit.trim().isEmpty()
                ? (item == null ? null : item.getAmountUnit())
                : requestedUnit.trim().toLowerCase();
        return unit;
    }

    private BigDecimal resolveNutritionBasisMultiplier(PantryItem item, String consumedUnit, BigDecimal consumedAmount) {
        if (item == null || item.getMicronutrients() == null || consumedAmount == null) {
            return null;
        }

        PantryItemMicronutrients micronutrients = item.getMicronutrients();
        BigDecimal basisAmount = micronutrients.getNutritionBasisAmount();
        if (basisAmount == null || basisAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal consumedBasisAmount = resolveConsumedBasisAmount(item, consumedUnit, consumedAmount);
        if (consumedBasisAmount == null || consumedBasisAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        return consumedBasisAmount.divide(basisAmount, 10, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveConsumedBasisAmount(PantryItem item, String consumedUnit, BigDecimal consumedAmount) {
        if (item == null || item.getMicronutrients() == null || consumedUnit == null || consumedAmount == null) {
            return null;
        }

        PantryItemMicronutrients micronutrients = item.getMicronutrients();
        String basisUnit = micronutrients.getNutritionBasisUnit();
        if (basisUnit == null) {
            return null;
        }

        if (basisUnit.equals(consumedUnit)) {
            return consumedAmount;
        }
        if ("serving".equals(consumedUnit)
                && basisUnit.equals(micronutrients.getServingQuantityUnit())
                && isPositive(micronutrients.getServingQuantityValue())) {
            return consumedAmount.multiply(micronutrients.getServingQuantityValue());
        }
        if ("package".equals(consumedUnit)
                && basisUnit.equals(micronutrients.getPackageQuantityUnit())
                && isPositive(micronutrients.getPackageQuantityValue())) {
            return consumedAmount.multiply(micronutrients.getPackageQuantityValue());
        }

        return null;
    }

    private BigDecimal resolveInventoryAmountToSubtract(PantryItem item, String consumedUnit, BigDecimal consumedAmount) {
        if (item == null || consumedUnit == null || consumedAmount == null) {
            return null;
        }

        String inventoryUnit = item.getAmountUnit();
        if (consumedUnit.equals(inventoryUnit)) {
            return consumedAmount;
        }

        PantryItemMicronutrients micronutrients = item.getMicronutrients();
        if (micronutrients == null) {
            return null;
        }

        if ("package".equals(inventoryUnit)) {
            BigDecimal consumedBasisAmount = resolveConsumedBasisAmount(item, consumedUnit, consumedAmount);
            BigDecimal packageQuantity = micronutrients.getPackageQuantityValue();
            if (consumedBasisAmount != null && isPositive(packageQuantity)) {
                return consumedBasisAmount.divide(packageQuantity, 10, RoundingMode.HALF_UP);
            }
        }

        if (("g".equals(inventoryUnit) || "ml".equals(inventoryUnit))) {
            BigDecimal consumedBasisAmount = resolveConsumedBasisAmount(item, consumedUnit, consumedAmount);
            if (consumedBasisAmount != null && inventoryUnit.equals(micronutrients.getNutritionBasisUnit())) {
                return consumedBasisAmount;
            }
        }

        return null;
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    public ConsumeResult removeItem(Long householdId, Long itemId, Double amount, Long authenticatedUserId) {
        // Issue #133 — amount (Double) replaces quantity (Integer)
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }

        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found."));

        HouseholdMemberId membershipId = new HouseholdMemberId(authenticatedUserId, household.getId());
        boolean isMember = householdMemberRepository.existsById(membershipId);
        if (!isMember) {
            throw new IllegalArgumentException("User is not a member of this household.");
        }

        PantryItem pantryItem = pantryItemRepository.findByIdAndHouseholdId(itemId, householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pantry item not found in this household."));

        double removeAmount = amount;
        if (removeAmount > safeAmount(pantryItem.getAmount())) {
            throw new IllegalArgumentException("Removed quantity exceeds available quantity.");
        }

        double remainingAmount = safeAmount(pantryItem.getAmount()) - removeAmount;

        ConsumeResult result = new ConsumeResult();
        result.setItemId(pantryItem.getId());
        result.setConsumedCalories(0.0);

        if (remainingAmount <= 0) {
            pantryItemRepository.delete(pantryItem);
            result.setRemainingAmount(0.0);
            result.setRemoved(true);
        }
        else {
            pantryItem.setAmount(remainingAmount);
            pantryItemRepository.save(pantryItem);
            result.setRemainingAmount(remainingAmount);
            result.setRemoved(false);
        }

        User actor = userRepository.findById(authenticatedUserId).orElse(null);
        PantryUpdateMessage msg = new PantryUpdateMessage();
        msg.setEventType(result.isRemoved() ? "ITEM_REMOVED" : "ITEM_UPDATED");
        msg.setHouseholdId(householdId);
        msg.setTriggeredByUserId(authenticatedUserId);
        msg.setTriggeredByUsername(actor != null ? actor.getUsername() : null);
        msg.setTimestamp(Instant.now().toString());
        msg.setNewTotalCalories(calculateTotalCalories(householdId));
        pantryBroadcastService.broadcastPantryUpdate(householdId, msg);

        return result;
    }

    public static class ConsumeResult {
        private Long itemId;
        private Double remainingAmount;
        private Double consumedCalories;
        private boolean removed;

        public Long getItemId() {
            return itemId;
        }

        public void setItemId(Long itemId) {
            this.itemId = itemId;
        }

        public Double getRemainingAmount() {
            return remainingAmount;
        }

        public void setRemainingAmount(Double remainingAmount) {
            this.remainingAmount = remainingAmount;
        }

        /**
         * @deprecated Use {@link #getRemainingAmount()} instead. Kept for compatibility.
         */
        @Deprecated
        public Integer getRemainingCount() {
            return remainingAmount == null ? null : remainingAmount.intValue();
        }

        /**
         * @deprecated Use {@link #setRemainingAmount(Double)} instead. Kept for compatibility.
         */
        @Deprecated
        public void setRemainingCount(Integer remainingCount) {
            this.remainingAmount = remainingCount == null ? null : remainingCount.doubleValue();
        }

        public Double getConsumedCalories() {
            return consumedCalories;
        }

        public void setConsumedCalories(Double consumedCalories) {
            this.consumedCalories = consumedCalories;
        }

        public boolean isRemoved() {
            return removed;
        }

        public void setRemoved(boolean removed) {
            this.removed = removed;
        }
    }
}
