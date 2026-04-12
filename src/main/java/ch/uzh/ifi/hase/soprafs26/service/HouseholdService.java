package ch.uzh.ifi.hase.soprafs26.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.entity.ConsumptionLog;
import ch.uzh.ifi.hase.soprafs26.entity.Household;
import ch.uzh.ifi.hase.soprafs26.entity.HouseholdBudget;
import ch.uzh.ifi.hase.soprafs26.entity.HouseholdMember;
import ch.uzh.ifi.hase.soprafs26.entity.HouseholdMemberId;
import ch.uzh.ifi.hase.soprafs26.repository.ConsumptionLogRepository;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdBudgetRepository;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdMemberRepository;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdStatsGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdStatsGetDTO.ComparisonToBudgetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdStatsGetDTO.DailyBreakdownDTO;

@Service
@Transactional
public class HouseholdService {

    private static final String INVITE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int INVITE_CODE_LENGTH = 6;
    private static final int INVITE_CODE_MAX_ATTEMPTS = 10;
    private static final Duration INVITE_CODE_TTL = Duration.ofDays(7);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final ConsumptionLogRepository consumptionLogRepository;
    private final HouseholdBudgetRepository householdBudgetRepository;

    public HouseholdService(HouseholdRepository householdRepository,
            HouseholdMemberRepository householdMemberRepository,
            ConsumptionLogRepository consumptionLogRepository,
            HouseholdBudgetRepository householdBudgetRepository) {
        this.householdRepository = householdRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.consumptionLogRepository = consumptionLogRepository;
        this.householdBudgetRepository = householdBudgetRepository;
    }

    public Household createHousehold(String name, Long ownerId) {
        if (name == null || name.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Household name must not be empty.");
        }

        Household household = new Household();
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

    public Household regenerateInviteCode(Long householdId, Long requesterUserId) {
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found."));

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

    public HouseholdBudget getBudget(Long householdId, Long requesterUserId) {
        if (!householdRepository.existsById(householdId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found.");
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found."));

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
        budget = householdBudgetRepository.save(budget);
        householdBudgetRepository.flush();
        return budget;
    }

    public HouseholdStatsGetDTO getStats(Long householdId, String startDateStr, String endDateStr, Long requesterUserId) {
        if (!householdRepository.existsById(householdId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found.");
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

    private String generateInviteCode() {
        StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            sb.append(INVITE_CODE_CHARS.charAt(RANDOM.nextInt(INVITE_CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
