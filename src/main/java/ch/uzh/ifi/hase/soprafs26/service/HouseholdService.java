package ch.uzh.ifi.hase.soprafs26.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.entity.ConsumptionLog;
import ch.uzh.ifi.hase.soprafs26.entity.Household;
import ch.uzh.ifi.hase.soprafs26.entity.PantryItem;
import ch.uzh.ifi.hase.soprafs26.entity.HouseholdBudget;
import ch.uzh.ifi.hase.soprafs26.entity.HouseholdMember;
import ch.uzh.ifi.hase.soprafs26.entity.HouseholdMemberId;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.ConsumptionLogRepository;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdBudgetRepository;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdMemberRepository;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdRepository;
import ch.uzh.ifi.hase.soprafs26.repository.PantryItemRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ConsumptionLogGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdMemberGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdStatsGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdStatsGetDTO.ComparisonToBudgetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdStatsGetDTO.DailyBreakdownDTO;

@Service
@Transactional
public class HouseholdService {

    public record HouseholdAccess(Household household, String role) {
    }

    private static final String MSG_HOUSEHOLD_NOT_FOUND = "Household not found.";

    private static final int CONSUMPTION_LOGS_DEFAULT_LIMIT = 20;
    private static final int CONSUMPTION_LOGS_MAX_LIMIT = 100;
    private static final String REMOVED_PRODUCT_LABEL = "Removed item";

    private static final String INVITE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int INVITE_CODE_LENGTH = 6;
    private static final int INVITE_CODE_MAX_ATTEMPTS = 10;
    private static final Duration INVITE_CODE_TTL = Duration.ofDays(7);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final ConsumptionLogRepository consumptionLogRepository;
    private final HouseholdBudgetRepository householdBudgetRepository;
    private final PantryItemRepository pantryItemRepository;
    private final UserRepository userRepository;

    public HouseholdService(HouseholdRepository householdRepository,
            HouseholdMemberRepository householdMemberRepository,
            ConsumptionLogRepository consumptionLogRepository,
            HouseholdBudgetRepository householdBudgetRepository,
            PantryItemRepository pantryItemRepository,
            UserRepository userRepository) {
        this.householdRepository = householdRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.consumptionLogRepository = consumptionLogRepository;
        this.householdBudgetRepository = householdBudgetRepository;
        this.pantryItemRepository = pantryItemRepository;
        this.userRepository = userRepository;
    }

    public Household createHousehold(String name, Long ownerId) {
        if (name == null || name.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Household name must not be empty.");
        }

        Household household = new Household();
        household.setId(null);
        household.setName(name.trim());
        household.setOwnerId(ownerId);
        refreshInviteCode(household);
        household = householdRepository.save(household);
        householdRepository.flush();

        HouseholdMemberId memberId = new HouseholdMemberId(ownerId, household.getId());
        HouseholdMember member = new HouseholdMember();
        member.setId(memberId);
        householdMemberRepository.save(member);
        householdMemberRepository.flush();

        return household;
    }

    public List<HouseholdAccess> getHouseholdsForUser(Long requesterUserId) {
        List<HouseholdMember> memberships = householdMemberRepository.findByIdUserId(requesterUserId);
        if (memberships.isEmpty()) {
            return List.of();
        }

        List<Long> householdIds = memberships.stream()
                .map(HouseholdMember::getId)
                .map(HouseholdMemberId::getHouseholdId)
                .distinct()
                .toList();

        Map<Long, Household> householdsById = new LinkedHashMap<>();
        for (Household household : householdRepository.findAllById(householdIds)) {
            householdsById.put(household.getId(), household);
        }

        return householdIds.stream()
                .map(householdsById::get)
                .filter(Objects::nonNull)
                .map(household -> new HouseholdAccess(household, resolveRole(household, requesterUserId)))
                .toList();
    }

    public HouseholdAccess getHouseholdForUser(Long householdId, Long requesterUserId) {
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, MSG_HOUSEHOLD_NOT_FOUND));

        HouseholdMemberId membershipId = new HouseholdMemberId(requesterUserId, householdId);
        if (!householdMemberRepository.existsById(membershipId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a member of this household.");
        }

        return new HouseholdAccess(household, resolveRole(household, requesterUserId));
    }

    public void deleteHousehold(Long householdId, Long requesterUserId) {
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, MSG_HOUSEHOLD_NOT_FOUND));

        if (!household.getOwnerId().equals(requesterUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the household owner can delete this household.");
        }

        consumptionLogRepository.deleteByHouseholdId(householdId);
        pantryItemRepository.deleteByHouseholdId(householdId);
        householdMemberRepository.deleteByIdHouseholdId(householdId);
        householdRepository.delete(household);
        householdRepository.flush();
    }

    public Household regenerateInviteCode(Long householdId, Long requesterUserId) {
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, MSG_HOUSEHOLD_NOT_FOUND));

        if (!household.getOwnerId().equals(requesterUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the household owner can generate invite codes.");
        }

        refreshInviteCode(household);
        household = householdRepository.save(household);
        householdRepository.flush();
        return household;
    }

    public Household joinHouseholdByInviteCode(String inviteCode, Long requesterUserId) {
        if (inviteCode == null || inviteCode.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invite code must not be empty.");
        }

        String normalizedCode = inviteCode.trim().toUpperCase();
        Household household = householdRepository.findByInviteCode(normalizedCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite code is invalid."));

        Instant expiresAt = household.getInviteCodeExpiresAt();
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Invite code has expired. Please request a new code.");
        }

        HouseholdMemberId memberId = new HouseholdMemberId(requesterUserId, household.getId());
        if (householdMemberRepository.existsById(memberId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a member of this household.");
        }

        HouseholdMember member = new HouseholdMember();
        member.setId(memberId);
        householdMemberRepository.save(member);
        householdMemberRepository.flush();
        return household;
    }

    private String resolveRole(Household household, Long requesterUserId) {
        return household.getOwnerId().equals(requesterUserId) ? "owner" : "member";
    }
    public HouseholdBudget getBudget(Long householdId, Long requesterUserId) {
        if (!householdRepository.existsById(householdId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, MSG_HOUSEHOLD_NOT_FOUND);
        }

        HouseholdMemberId memberId = new HouseholdMemberId(requesterUserId, householdId);
        if (!householdMemberRepository.existsById(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this household.");
        }

        return householdBudgetRepository.findByHouseholdId(householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No budget set for this household."));
    }

    public HouseholdBudget updateBudget(Long householdId, Double dailyCalorieTarget, Long requesterUserId) {
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, MSG_HOUSEHOLD_NOT_FOUND));

        if (!household.getOwnerId().equals(requesterUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the household owner can update the budget.");
        }

        if (dailyCalorieTarget == null || dailyCalorieTarget <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Daily calorie target must be greater than 0.");
        }

        HouseholdBudget budget = householdBudgetRepository.findByHouseholdId(householdId)
                .orElse(new HouseholdBudget());
        budget.setHouseholdId(householdId);
        budget.setDailyCalorieTarget(dailyCalorieTarget);
        try {
            budget = householdBudgetRepository.save(budget);
            householdBudgetRepository.flush();
            return budget;
        } catch (DataIntegrityViolationException e) {
            // Concurrent insert: another request already created the budget row, update it instead
            budget = householdBudgetRepository.findByHouseholdId(householdId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Budget conflict."));
            budget.setDailyCalorieTarget(dailyCalorieTarget);
            return householdBudgetRepository.save(budget);
        }
    }

    public HouseholdStatsGetDTO getStats(Long householdId, String startDateStr, String endDateStr, Long requesterUserId) {
        if (!householdRepository.existsById(householdId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, MSG_HOUSEHOLD_NOT_FOUND);
        }

        HouseholdMemberId memberId = new HouseholdMemberId(requesterUserId, householdId);
        if (!householdMemberRepository.existsById(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this household.");
        }

        LocalDate startDate;
        LocalDate endDate;
        try {
            startDate = LocalDate.parse(startDateStr);
            endDate = LocalDate.parse(endDateStr);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format. Expected YYYY-MM-DD.");
        }

        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must not be before startDate.");
        }

        Instant rangeStart = startDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant rangeEnd = endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<ConsumptionLog> logs = consumptionLogRepository
                .findByHouseholdIdAndConsumedAtBetween(householdId, rangeStart, rangeEnd);

        List<DailyBreakdownDTO> dailyBreakdown = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            Instant dayStart = current.atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant dayEnd = current.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            double dayCalories = logs.stream()
                    .filter(log -> !log.getConsumedAt().isBefore(dayStart) && log.getConsumedAt().isBefore(dayEnd))
                    .mapToDouble(ConsumptionLog::getConsumedCalories)
                    .sum();
            dailyBreakdown.add(new DailyBreakdownDTO(current.toString(), dayCalories));
            current = current.plusDays(1);
        }

        double totalCalories = dailyBreakdown.stream()
                .mapToDouble(DailyBreakdownDTO::getCaloriesConsumed)
                .sum();
        long numDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        double avgDailyCalories = totalCalories / numDays;

        Double dailyTarget = householdBudgetRepository.findByHouseholdId(householdId)
                .map(HouseholdBudget::getDailyCalorieTarget)
                .orElse(null);

        ComparisonToBudgetDTO comparison = null;
        if (dailyTarget != null) {
            double diff = avgDailyCalories - dailyTarget;
            double pct = (avgDailyCalories / dailyTarget) * 100.0;
            String status;
            if (Math.abs(diff) <= dailyTarget * 0.05) {
                status = "ON_TARGET";
            } else if (diff > 0) {
                status = "OVER_BUDGET";
            } else {
                status = "UNDER_BUDGET";
            }
            comparison = new ComparisonToBudgetDTO(status, diff, pct);
        }

        HouseholdStatsGetDTO dto = new HouseholdStatsGetDTO();
        dto.setStartDate(startDateStr);
        dto.setEndDate(endDateStr);
        dto.setDailyCalorieTarget(dailyTarget);
        dto.setAverageDailyCalories(avgDailyCalories);
        dto.setTotalCaloriesConsumed(totalCalories);
        dto.setDailyBreakdown(dailyBreakdown);
        dto.setComparisonToBudget(comparison);
        return dto;
    }

    public List<ConsumptionLogGetDTO> getConsumptionLogs(Long householdId, Long requesterUserId, Integer limit) {
        if (!householdRepository.existsById(householdId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, MSG_HOUSEHOLD_NOT_FOUND);
        }

        HouseholdMemberId memberId = new HouseholdMemberId(requesterUserId, householdId);
        if (!householdMemberRepository.existsById(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this household.");
        }

        int size = limit == null ? CONSUMPTION_LOGS_DEFAULT_LIMIT : limit;
        size = Math.min(Math.max(size, 1), CONSUMPTION_LOGS_MAX_LIMIT);

        List<ConsumptionLog> logs = consumptionLogRepository.findByHouseholdIdOrderByConsumedAtDesc(
                householdId,
                PageRequest.of(0, size));

        return logs.stream()
                .map(log -> toConsumptionLogGetDTO(log, householdId))
                .toList();
    }

    private ConsumptionLogGetDTO toConsumptionLogGetDTO(ConsumptionLog log, Long householdId) {
        ConsumptionLogGetDTO dto = new ConsumptionLogGetDTO();
        dto.setLogId(log.getId());
        dto.setConsumedAt(log.getConsumedAt());
        dto.setPantryItemId(log.getPantryItemId());
        dto.setConsumedQuantity(log.getConsumedQuantity());
        dto.setConsumedCalories(log.getConsumedCalories());
        dto.setUserId(log.getUserId());
        String productName = pantryItemRepository.findByIdAndHouseholdId(log.getPantryItemId(), householdId)
                .map(PantryItem::getName)
                .orElse(REMOVED_PRODUCT_LABEL);
        dto.setProductName(productName);
        return dto;
    }

    private void refreshInviteCode(Household household) {
        household.setInviteCode(generateUniqueInviteCode());
        household.setInviteCodeExpiresAt(Instant.now().plus(INVITE_CODE_TTL));
    }

    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < INVITE_CODE_MAX_ATTEMPTS; attempt++) {
            String code = generateInviteCode();
            if (householdRepository.findByInviteCode(code).isEmpty()) {
                return code;
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate a unique invite code.");
    }

    public List<HouseholdMemberGetDTO> getMembers(Long householdId, Long authenticatedUserId) {
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, MSG_HOUSEHOLD_NOT_FOUND));

        HouseholdMemberId membershipId = new HouseholdMemberId(authenticatedUserId, householdId);
        if (!householdMemberRepository.existsById(membershipId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this household.");
        }

        List<HouseholdMember> members = householdMemberRepository.findByIdHouseholdId(householdId);
        return members.stream().map(member -> {
            Long userId = member.getId().getUserId();
            User user = userRepository.findById(userId).orElse(null);
            HouseholdMemberGetDTO dto = new HouseholdMemberGetDTO();
            dto.setUserId(userId);
            dto.setUsername(user != null ? user.getUsername() : "Unknown");
            dto.setRole(userId.equals(household.getOwnerId()) ? "owner" : "member");
            dto.setJoinedAt(member.getJoinedAt());
            return dto;
        }).toList();
    }

    private String generateInviteCode() {
        StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            sb.append(INVITE_CODE_CHARS.charAt(RANDOM.nextInt(INVITE_CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
