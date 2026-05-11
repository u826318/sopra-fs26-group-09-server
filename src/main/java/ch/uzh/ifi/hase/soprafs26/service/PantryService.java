package ch.uzh.ifi.hase.soprafs26.service;

import java.time.Instant;
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
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.ConsumptionLogRepository;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdMemberRepository;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdRepository;
import ch.uzh.ifi.hase.soprafs26.repository.PantryItemRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.PantryItemPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.PortionEstimateResponseDTO;
import ch.uzh.ifi.hase.soprafs26.websocket.PantryUpdateMessage;


@Service
@Transactional
public class PantryService {

    /**
     * Upper bound for {@link #bulkAddItems(Long, List, Long)} to avoid oversized payloads.
     */
    public static final int MAX_ITEMS_PER_BULK_REQUEST = 100;

    // Issue #114 — allowed values for amountUnit
    private static final Set<String> VALID_AMOUNT_UNITS = Set.of("g", "ml", "package");

    private final PantryItemRepository pantryItemRepository;
    private final ConsumptionLogRepository consumptionLogRepository;
    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final UserRepository userRepository;
    private final PantryBroadcastService pantryBroadcastService;
    private final PantryItemMicronutrientService pantryItemMicronutrientService;
    private final DailyNutrientIntakeService dailyNutrientIntakeService;
    private final MealPortionEstimateService mealPortionEstimateService;

    public PantryService(
            PantryItemRepository pantryItemRepository,
            ConsumptionLogRepository consumptionLogRepository,
            HouseholdRepository householdRepository,
            HouseholdMemberRepository householdMemberRepository,
            UserRepository userRepository,
            PantryBroadcastService pantryBroadcastService,
            PantryItemMicronutrientService pantryItemMicronutrientService,
            DailyNutrientIntakeService dailyNutrientIntakeService,
            MealPortionEstimateService mealPortionEstimateService
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
    }

    // Issue #114 — unit-aware calorie calculation for a single consume operation
    private Double computeConsumedCalories(PantryItem item, double consumedAmount) {
        String unit = item.getAmountUnit();
        if ("g".equals(unit) && item.getKcalPer100g() != null && item.getKcalPer100g() > 0) {
            return item.getKcalPer100g() * consumedAmount / 100.0;
        }
        if ("ml".equals(unit) && item.getKcalPer100ml() != null && item.getKcalPer100ml() > 0) {
            return item.getKcalPer100ml() * consumedAmount / 100.0;
        }
        if ("package".equals(unit) && item.getKcalPerPackage() != null && item.getKcalPerPackage() > 0) {
            return item.getKcalPerPackage() * consumedAmount;
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

        String normalizedBarcode = pantryItemPostDTO.getBarcode() != null ? pantryItemPostDTO.getBarcode().trim() : null;
        String normalizedName = pantryItemPostDTO.getName().trim();

        PantryItem saved = mergeOrCreatePantryItem(
                householdId,
                normalizedBarcode,
                normalizedName,
                pantryItemPostDTO.getAmountUnit(),
                pantryItemPostDTO.getAmount(),
                pantryItemPostDTO.getKcalPerPackage(),
                pantryItemPostDTO.getKcalPer100g(),
                pantryItemPostDTO.getKcalPer100ml()
        );
        pantryItemMicronutrientService.upsertMicronutrientsPerPackage(
                saved,
                pantryItemPostDTO.getPackageQuantity(),
                pantryItemPostDTO.getNutriments());

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
            String normalizedBarcode = dto.getBarcode() != null ? dto.getBarcode().trim() : null;
            String normalizedName = dto.getName().trim();
            PantryItem saved = mergeOrCreatePantryItem(
                    householdId,
                    normalizedBarcode,
                    normalizedName,
                    dto.getAmountUnit(),
                    dto.getAmount(),
                    dto.getKcalPerPackage(),
                    dto.getKcalPer100g(),
                    dto.getKcalPer100ml());
            pantryItemMicronutrientService.upsertMicronutrientsPerPackage(
                    saved,
                    dto.getPackageQuantity(),
                    dto.getNutriments());
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
            throw new IllegalArgumentException("Amount unit must be one of: g, ml, package.");
        }
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Product name must not be empty.");
        }
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
            Double kcalPer100ml
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
            matchingItem.setAmount(safeAmount(matchingItem.getAmount()) + safeAmount(amount));
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
        return consumeItem(householdId, itemId, amount, null, false, authenticatedUserId);
    }

    public ConsumeResult consumeItem(
            Long householdId,
            Long itemId,
            Double amount,
            Double kcalPerPackageOverride,
            boolean skipCalorieLogging,
            Long authenticatedUserId) {
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

        PantryItem pantryItem = pantryItemRepository.findByIdAndHouseholdId(itemId, householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pantry item not found in this household."));

        double consumeAmount = amount;
        if (consumeAmount > safeAmount(pantryItem.getAmount())) {
            throw new IllegalArgumentException("Consumed quantity exceeds available quantity.");
        }

        if (!skipCalorieLogging && kcalPerPackageOverride != null) {
            pantryItem.setKcalPerPackage(kcalPerPackageOverride);
            pantryItemRepository.save(pantryItem);
        }

        double remainingAmount = safeAmount(pantryItem.getAmount()) - consumeAmount;
        // Issue #114 — unit-aware calorie formula; Issue #133 — supports partial amounts
        Double consumedCalories = skipCalorieLogging ? null : computeConsumedCalories(pantryItem, consumeAmount);

        // Issue #133 — log consumed amount rounded to nearest int for display in activity feed
        int loggedQuantity = (int) Math.max(1, Math.round(consumeAmount));
        ConsumptionLog log = new ConsumptionLog();
        log.setHouseholdId(householdId);
        log.setUserId(authenticatedUserId);
        log.setPantryItemId(pantryItem.getId());
        log.setConsumedQuantity(loggedQuantity);
        // Issue #133 — persist unit so activity feed can display "200g" instead of "200×"
        log.setConsumedUnit(pantryItem.getAmountUnit());
        log.setConsumedCalories(consumedCalories);
        log.setConsumedAt(Instant.now());
        consumptionLogRepository.save(log);
        // Issue #133 — only track micronutrients for package unit (g/ml multiplier requires package size)
        if ("package".equals(pantryItem.getAmountUnit())) {
            dailyNutrientIntakeService.recordConsumedPantryItem(
                    authenticatedUserId,
                    pantryItem,
                    loggedQuantity,
                    log.getConsumedAt());
        }

        ConsumeResult result = new ConsumeResult();
        result.setItemId(pantryItem.getId());
        result.setConsumedCalories(consumedCalories);

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
        msg.setEventType("ITEM_CONSUMED");
        msg.setHouseholdId(householdId);
        msg.setTriggeredByUserId(authenticatedUserId);
        msg.setTriggeredByUsername(actor != null ? actor.getUsername() : null);
        msg.setTimestamp(Instant.now().toString());
        msg.setNewTotalCalories(calculateTotalCalories(householdId));
        pantryBroadcastService.broadcastPantryUpdate(householdId, msg);

        return result;
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
