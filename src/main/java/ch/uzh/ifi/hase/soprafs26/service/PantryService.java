package ch.uzh.ifi.hase.soprafs26.service;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import ch.uzh.ifi.hase.soprafs26.websocket.PantryUpdateMessage;

@Service
@Transactional
public class PantryService {

    private final PantryItemRepository pantryItemRepository;
    private final ConsumptionLogRepository consumptionLogRepository;
    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final UserRepository userRepository;
    private final PantryBroadcastService pantryBroadcastService;

    public PantryService(
            PantryItemRepository pantryItemRepository,
            ConsumptionLogRepository consumptionLogRepository,
            HouseholdRepository householdRepository,
            HouseholdMemberRepository householdMemberRepository,
            UserRepository userRepository,
            PantryBroadcastService pantryBroadcastService
    ) {
        this.pantryItemRepository = pantryItemRepository;
        this.consumptionLogRepository = consumptionLogRepository;
        this.householdRepository = householdRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.userRepository = userRepository;
        this.pantryBroadcastService = pantryBroadcastService;
    }

    /**
     * Calculates the total calories currently stored in the pantry for one household.
     * Formula: sum(kcalPerPackage * count)
     */
    public double calculateTotalCalories(Long householdId) {
        List<PantryItem> pantryItems = pantryItemRepository.findByHouseholdId(householdId);

        double totalCalories = 0.0;
        for (PantryItem item : pantryItems) {
            if (item.getKcalPerPackage() != null && item.getCount() != null) {
                totalCalories += item.getKcalPerPackage() * item.getCount();
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
        if (pantryItemPostDTO == null) {
            throw new IllegalArgumentException("Pantry item payload must not be empty.");
        }
        if (pantryItemPostDTO.getQuantity() == null || pantryItemPostDTO.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
        if (pantryItemPostDTO.getBarcode() == null || pantryItemPostDTO.getBarcode().trim().isEmpty()) {
            throw new IllegalArgumentException("Barcode must not be empty.");
        }
        if (pantryItemPostDTO.getName() == null || pantryItemPostDTO.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Product name must not be empty.");
        }
        if (pantryItemPostDTO.getKcalPerPackage() == null || pantryItemPostDTO.getKcalPerPackage() < 0) {
            throw new IllegalArgumentException("Calories per package must be zero or greater.");
        }

        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found."));

        HouseholdMemberId membershipId = new HouseholdMemberId(authenticatedUserId, household.getId());
        boolean isMember = householdMemberRepository.existsById(membershipId);
        if (!isMember) {
            throw new IllegalArgumentException("User is not a member of this household.");
        }

        String normalizedBarcode = pantryItemPostDTO.getBarcode().trim();
        String normalizedName = pantryItemPostDTO.getName().trim();

        PantryItem saved = mergeOrCreatePantryItem(
                householdId,
                normalizedBarcode,
                normalizedName,
                pantryItemPostDTO.getKcalPerPackage(),
                pantryItemPostDTO.getQuantity()
        );

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
        payload.setQuantity(saved.getCount().doubleValue());
        payload.setCaloriesPerUnit(saved.getKcalPerPackage());
        payload.setAddedByUserId(authenticatedUserId);
        payload.setAddedAt(saved.getAddedAt().toString());
        msg.setItem(payload);
        pantryBroadcastService.broadcastPantryUpdate(householdId, msg);

        return saved;
    }

    private PantryItem mergeOrCreatePantryItem(
            Long householdId,
            String barcode,
            String name,
            Double kcalPerPackage,
            Integer quantity
    ) {
        String normalizedBarcode = normalizeBarcode(barcode);

        List<PantryItem> matchingItems = pantryItemRepository
                .findByHouseholdIdAndBarcode(householdId, normalizedBarcode);

        PantryItem matchingItem = matchingItems.isEmpty() ? null : matchingItems.get(0);

        if (matchingItem != null) {
            matchingItem.setName(name);
            matchingItem.setKcalPerPackage(kcalPerPackage);
            matchingItem.setCount(safeCount(matchingItem.getCount()) + safeCount(quantity));
            return pantryItemRepository.save(matchingItem);
        }

        PantryItem pantryItem = new PantryItem();
        pantryItem.setHouseholdId(householdId);
        pantryItem.setBarcode(normalizedBarcode);
        pantryItem.setName(name);
        pantryItem.setKcalPerPackage(kcalPerPackage);
        pantryItem.setCount(safeCount(quantity));
        pantryItem.setAddedAt(Instant.now());

        return pantryItemRepository.save(pantryItem);
    }

    private String normalizeBarcode(String barcode) {
        if (barcode == null) {
            return null;
        }

        String trimmedBarcode = barcode.trim();
        return trimmedBarcode.isEmpty() ? null : trimmedBarcode;
    }

    private int safeCount(Integer count) {
        return count == null ? 0 : count;
    }

    public ConsumeResult consumeItem(Long householdId, Long itemId, Integer quantity, Long authenticatedUserId) {
        if (quantity == null || quantity <= 0) {
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

        if (quantity > pantryItem.getCount()) {
            throw new IllegalArgumentException("Consumed quantity exceeds available quantity.");
        }

        int remainingCount = pantryItem.getCount() - quantity;
        double consumedCalories = pantryItem.getKcalPerPackage() * quantity;

        ConsumptionLog log = new ConsumptionLog();
        log.setHouseholdId(householdId);
        log.setUserId(authenticatedUserId);
        log.setPantryItemId(pantryItem.getId());
        log.setConsumedQuantity(quantity);
        log.setConsumedCalories(consumedCalories);
        log.setConsumedAt(Instant.now());
        consumptionLogRepository.save(log);

        ConsumeResult result = new ConsumeResult();
        result.setItemId(pantryItem.getId());
        result.setConsumedCalories(consumedCalories);

        if (remainingCount == 0) {
            pantryItemRepository.delete(pantryItem);
            result.setRemainingCount(0);
            result.setRemoved(true);
        }
        else {
            pantryItem.setCount(remainingCount);
            pantryItemRepository.save(pantryItem);
            result.setRemainingCount(remainingCount);
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

    public ConsumeResult removeItem(Long householdId, Long itemId, Integer quantity, Long authenticatedUserId) {
        if (quantity == null || quantity <= 0) {
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

        if (quantity > pantryItem.getCount()) {
            throw new IllegalArgumentException("Removed quantity exceeds available quantity.");
        }

        int remainingCount = pantryItem.getCount() - quantity;

        ConsumeResult result = new ConsumeResult();
        result.setItemId(pantryItem.getId());
        result.setConsumedCalories(0.0);

        if (remainingCount == 0) {
            pantryItemRepository.delete(pantryItem);
            result.setRemainingCount(0);
            result.setRemoved(true);
        }
        else {
            pantryItem.setCount(remainingCount);
            pantryItemRepository.save(pantryItem);
            result.setRemainingCount(remainingCount);
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
        private Integer remainingCount;
        private Double consumedCalories;
        private boolean removed;

        public Long getItemId() {
            return itemId;
        }

        public void setItemId(Long itemId) {
            this.itemId = itemId;
        }

        public Integer getRemainingCount() {
            return remainingCount;
        }

        public void setRemainingCount(Integer remainingCount) {
            this.remainingCount = remainingCount;
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
