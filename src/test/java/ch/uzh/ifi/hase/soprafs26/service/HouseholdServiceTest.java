package ch.uzh.ifi.hase.soprafs26.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.entity.ConsumptionLog;
import ch.uzh.ifi.hase.soprafs26.entity.Household;
import ch.uzh.ifi.hase.soprafs26.entity.HouseholdBudget;
import ch.uzh.ifi.hase.soprafs26.entity.HouseholdMemberId;
import ch.uzh.ifi.hase.soprafs26.repository.ConsumptionLogRepository;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdBudgetRepository;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdMemberRepository;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdStatsGetDTO;

class HouseholdServiceTest {

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private HouseholdMemberRepository householdMemberRepository;

    @Mock
    private ConsumptionLogRepository consumptionLogRepository;

    @Mock
    private HouseholdBudgetRepository householdBudgetRepository;

    @InjectMocks
    private HouseholdService householdService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

        when(householdRepository.save(any())).thenAnswer(inv -> {
            Household h = inv.getArgument(0);
            h.setId(10L);
            return h;
        });
        when(householdRepository.findByInviteCode(anyString())).thenReturn(Optional.empty());
        when(householdMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── createHousehold ──────────────────────────────────────────────────────

    @Test
    void createHousehold_validInput_success() {
        Household result = householdService.createHousehold("Smith Family", 1L);

        assertNotNull(result);
        assertEquals("Smith Family", result.getName());
        assertEquals(1L, result.getOwnerId());
        assertNotNull(result.getInviteCode());
        assertNotNull(result.getInviteCodeExpiresAt());
        assertEquals(6, result.getInviteCode().length());
        verify(householdRepository).save(any());
        verify(householdMemberRepository).save(any());
    }

    @Test
    void createHousehold_nameTrimsWhitespace() {
        Household result = householdService.createHousehold("  My House  ", 1L);
        assertEquals("My House", result.getName());
    }

    @Test
    void createHousehold_emptyName_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> householdService.createHousehold("", 1L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createHousehold_nullName_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> householdService.createHousehold(null, 1L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createHousehold_inviteCodeCollision_retriesAndSucceeds() {
        when(householdRepository.findByInviteCode(anyString()))
                .thenReturn(Optional.of(new Household()))
                .thenReturn(Optional.empty());

        Household result = householdService.createHousehold("Test House", 1L);
        assertNotNull(result.getInviteCode());
    }

    @Test
    void createHousehold_inviteCodeMaxAttemptsExceeded_throws500() {
        when(householdRepository.findByInviteCode(anyString()))
                .thenReturn(Optional.of(new Household()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> householdService.createHousehold("Test House", 1L));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatusCode());
    }

    // ── regenerateInviteCode ─────────────────────────────────────────────────

    @Test
    void regenerateInviteCode_owner_success() {
        Household existing = new Household();
        existing.setId(10L);
        existing.setOwnerId(1L);
        existing.setInviteCode("OLD111");

        when(householdRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(householdRepository.findByInviteCode(anyString()))
                .thenReturn(Optional.of(new Household()))
                .thenReturn(Optional.empty());

        Household result = householdService.regenerateInviteCode(10L, 1L);
        assertNotNull(result.getInviteCode());
        assertEquals(6, result.getInviteCode().length());
    }

    @Test
    void regenerateInviteCode_nonOwner_forbidden() {
        Household existing = new Household();
        existing.setId(10L);
        existing.setOwnerId(2L);

        when(householdRepository.findById(10L)).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> householdService.regenerateInviteCode(10L, 1L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    // ── joinHouseholdByInviteCode ────────────────────────────────────────────

    @Test
    void joinHouseholdByInviteCode_validCode_success() {
        Household household = new Household();
        household.setId(20L);
        household.setInviteCode("ABC123");
        household.setInviteCodeExpiresAt(Instant.now().plusSeconds(60));

        when(householdRepository.findByInviteCode("ABC123")).thenReturn(Optional.of(household));
        when(householdMemberRepository.existsById(eq(new HouseholdMemberId(1L, 20L)))).thenReturn(false);

        Household result = householdService.joinHouseholdByInviteCode("abc123", 1L);
        assertEquals(20L, result.getId());
        verify(householdMemberRepository).save(any());
    }

    @Test
    void joinHouseholdByInviteCode_expiredCode_gone() {
        Household household = new Household();
        household.setId(20L);
        household.setInviteCode("ABC123");
        household.setInviteCodeExpiresAt(Instant.now().minusSeconds(1));

        when(householdRepository.findByInviteCode("ABC123")).thenReturn(Optional.of(household));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> householdService.joinHouseholdByInviteCode("ABC123", 1L));
        assertEquals(HttpStatus.GONE, ex.getStatusCode());
    }

    @Test
    void joinHouseholdByInviteCode_existingMember_conflict() {
        Household household = new Household();
        household.setId(20L);
        household.setInviteCode("ABC123");
        household.setInviteCodeExpiresAt(Instant.now().plusSeconds(60));

        when(householdRepository.findByInviteCode("ABC123")).thenReturn(Optional.of(household));
        when(householdMemberRepository.existsById(eq(new HouseholdMemberId(1L, 20L)))).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> householdService.joinHouseholdByInviteCode("ABC123", 1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    // ── getBudget ────────────────────────────────────────────────────────────

    @Test
    void getBudget_member_returnsBudget() {
        HouseholdBudget budget = new HouseholdBudget();
        budget.setId(1L);
        budget.setHouseholdId(10L);
        budget.setDailyCalorieTarget(2000.0);

        when(householdRepository.existsById(10L)).thenReturn(true);
        when(householdMemberRepository.existsById(eq(new HouseholdMemberId(1L, 10L)))).thenReturn(true);
        when(householdBudgetRepository.findByHouseholdId(10L)).thenReturn(Optional.of(budget));

        HouseholdBudget result = householdService.getBudget(10L, 1L);
        assertEquals(2000.0, result.getDailyCalorieTarget());
    }

    @Test
    void getBudget_noBudgetSet_throws404() {
        when(householdRepository.existsById(10L)).thenReturn(true);
        when(householdMemberRepository.existsById(eq(new HouseholdMemberId(1L, 10L)))).thenReturn(true);
        when(householdBudgetRepository.findByHouseholdId(10L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> householdService.getBudget(10L, 1L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getBudget_notMember_forbidden() {
        when(householdRepository.existsById(10L)).thenReturn(true);
        when(householdMemberRepository.existsById(eq(new HouseholdMemberId(1L, 10L)))).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> householdService.getBudget(10L, 1L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void getBudget_householdNotFound_throws404() {
        when(householdRepository.existsById(99L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> householdService.getBudget(99L, 1L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // ── updateBudget ─────────────────────────────────────────────────────────

    @Test
    void updateBudget_owner_createsNew() {
        Household household = new Household();
        household.setId(10L);
        household.setOwnerId(1L);

        when(householdRepository.findById(10L)).thenReturn(Optional.of(household));
        when(householdBudgetRepository.findByHouseholdId(10L)).thenReturn(Optional.empty());
        when(householdBudgetRepository.save(any())).thenAnswer(inv -> {
            HouseholdBudget b = inv.getArgument(0);
            b.setId(1L);
            return b;
        });

        HouseholdBudget result = householdService.updateBudget(10L, 2000.0, 1L);
        assertEquals(2000.0, result.getDailyCalorieTarget());
        assertEquals(10L, result.getHouseholdId());
    }

    @Test
    void updateBudget_owner_updatesExisting() {
        Household household = new Household();
        household.setId(10L);
        household.setOwnerId(1L);

        HouseholdBudget existing = new HouseholdBudget();
        existing.setId(1L);
        existing.setHouseholdId(10L);
        existing.setDailyCalorieTarget(1500.0);

        when(householdRepository.findById(10L)).thenReturn(Optional.of(household));
        when(householdBudgetRepository.findByHouseholdId(10L)).thenReturn(Optional.of(existing));
        when(householdBudgetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HouseholdBudget result = householdService.updateBudget(10L, 2500.0, 1L);
        assertEquals(2500.0, result.getDailyCalorieTarget());
    }

    @Test
    void updateBudget_nonOwner_forbidden() {
        Household household = new Household();
        household.setId(10L);
        household.setOwnerId(2L);

        when(householdRepository.findById(10L)).thenReturn(Optional.of(household));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> householdService.updateBudget(10L, 2000.0, 1L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void updateBudget_zeroCalorias_throwsBadRequest() {
        Household household = new Household();
        household.setId(10L);
        household.setOwnerId(1L);

        when(householdRepository.findById(10L)).thenReturn(Optional.of(household));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> householdService.updateBudget(10L, 0.0, 1L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateBudget_nullTarget_throwsBadRequest() {
        Household household = new Household();
        household.setId(10L);
        household.setOwnerId(1L);

        when(householdRepository.findById(10L)).thenReturn(Optional.of(household));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> householdService.updateBudget(10L, null, 1L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateBudget_householdNotFound_throws404() {
        when(householdRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> householdService.updateBudget(99L, 2000.0, 1L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // ── getStats ─────────────────────────────────────────────────────────────

    @Test
    void getStats_withBudget_returnsBreakdownAndComparison() {
        ConsumptionLog log = new ConsumptionLog();
        log.setConsumedCalories(1800.0);
        log.setConsumedAt(Instant.parse("2026-04-10T12:00:00Z"));

        HouseholdBudget budget = new HouseholdBudget();
        budget.setDailyCalorieTarget(2000.0);

        when(householdRepository.existsById(10L)).thenReturn(true);
        when(householdMemberRepository.existsById(eq(new HouseholdMemberId(1L, 10L)))).thenReturn(true);
        when(consumptionLogRepository.findByHouseholdIdAndConsumedAtBetween(eq(10L), any(), any()))
                .thenReturn(List.of(log));
        when(householdBudgetRepository.findByHouseholdId(10L)).thenReturn(Optional.of(budget));

        HouseholdStatsGetDTO result = householdService.getStats(10L, "2026-04-10", "2026-04-10", 1L);

        assertEquals("2026-04-10", result.getStartDate());
        assertEquals("2026-04-10", result.getEndDate());
        assertEquals(1800.0, result.getTotalCaloriesConsumed());
        assertEquals(1800.0, result.getAverageDailyCalories());
        assertEquals(2000.0, result.getDailyCalorieTarget());
        assertEquals(1, result.getDailyBreakdown().size());
        assertEquals("2026-04-10", result.getDailyBreakdown().get(0).getDate());
        assertEquals(1800.0, result.getDailyBreakdown().get(0).getCaloriesConsumed());
        assertNotNull(result.getComparisonToBudget());
        assertEquals("UNDER_BUDGET", result.getComparisonToBudget().getStatus());
    }

    @Test
    void getStats_onTarget_withinFivePercent() {
        ConsumptionLog log = new ConsumptionLog();
        log.setConsumedCalories(2050.0);
        log.setConsumedAt(Instant.parse("2026-04-10T12:00:00Z"));

        HouseholdBudget budget = new HouseholdBudget();
        budget.setDailyCalorieTarget(2000.0);

        when(householdRepository.existsById(10L)).thenReturn(true);
        when(householdMemberRepository.existsById(any())).thenReturn(true);
        when(consumptionLogRepository.findByHouseholdIdAndConsumedAtBetween(any(), any(), any()))
                .thenReturn(List.of(log));
        when(householdBudgetRepository.findByHouseholdId(10L)).thenReturn(Optional.of(budget));

        HouseholdStatsGetDTO result = householdService.getStats(10L, "2026-04-10", "2026-04-10", 1L);
        assertEquals("ON_TARGET", result.getComparisonToBudget().getStatus());
    }

    @Test
    void getStats_overBudget_returnsOverBudget() {
        ConsumptionLog log = new ConsumptionLog();
        log.setConsumedCalories(2500.0);
        log.setConsumedAt(Instant.parse("2026-04-10T12:00:00Z"));

        HouseholdBudget budget = new HouseholdBudget();
        budget.setDailyCalorieTarget(2000.0);

        when(householdRepository.existsById(10L)).thenReturn(true);
        when(householdMemberRepository.existsById(any())).thenReturn(true);
        when(consumptionLogRepository.findByHouseholdIdAndConsumedAtBetween(any(), any(), any()))
                .thenReturn(List.of(log));
        when(householdBudgetRepository.findByHouseholdId(10L)).thenReturn(Optional.of(budget));

        HouseholdStatsGetDTO result = householdService.getStats(10L, "2026-04-10", "2026-04-10", 1L);
        assertEquals("OVER_BUDGET", result.getComparisonToBudget().getStatus());
    }

    @Test
    void getStats_noBudget_comparisonIsNull() {
        when(householdRepository.existsById(10L)).thenReturn(true);
        when(householdMemberRepository.existsById(any())).thenReturn(true);
        when(consumptionLogRepository.findByHouseholdIdAndConsumedAtBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(householdBudgetRepository.findByHouseholdId(10L)).thenReturn(Optional.empty());

        HouseholdStatsGetDTO result = householdService.getStats(10L, "2026-04-10", "2026-04-10", 1L);
        assertNull(result.getDailyCalorieTarget());
        assertNull(result.getComparisonToBudget());
        assertEquals(0.0, result.getTotalCaloriesConsumed());
    }

    @Test
    void getStats_multiDay_breakdownPerDay() {
        ConsumptionLog log1 = new ConsumptionLog();
        log1.setConsumedCalories(1000.0);
        log1.setConsumedAt(Instant.parse("2026-04-10T12:00:00Z"));

        ConsumptionLog log2 = new ConsumptionLog();
        log2.setConsumedCalories(1500.0);
        log2.setConsumedAt(Instant.parse("2026-04-11T12:00:00Z"));

        when(householdRepository.existsById(10L)).thenReturn(true);
        when(householdMemberRepository.existsById(any())).thenReturn(true);
        when(consumptionLogRepository.findByHouseholdIdAndConsumedAtBetween(any(), any(), any()))
                .thenReturn(List.of(log1, log2));
        when(householdBudgetRepository.findByHouseholdId(10L)).thenReturn(Optional.empty());

        HouseholdStatsGetDTO result = householdService.getStats(10L, "2026-04-10", "2026-04-11", 1L);

        assertEquals(2500.0, result.getTotalCaloriesConsumed());
        assertEquals(1250.0, result.getAverageDailyCalories());
        assertEquals(2, result.getDailyBreakdown().size());
        assertEquals(1000.0, result.getDailyBreakdown().get(0).getCaloriesConsumed());
        assertEquals(1500.0, result.getDailyBreakdown().get(1).getCaloriesConsumed());
    }

    @Test
    void getStats_endBeforeStart_throwsBadRequest() {
        when(householdRepository.existsById(10L)).thenReturn(true);
        when(householdMemberRepository.existsById(any())).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> householdService.getStats(10L, "2026-04-11", "2026-04-10", 1L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getStats_invalidDate_throwsBadRequest() {
        when(householdRepository.existsById(10L)).thenReturn(true);
        when(householdMemberRepository.existsById(any())).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> householdService.getStats(10L, "not-a-date", "2026-04-10", 1L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getStats_notMember_forbidden() {
        when(householdRepository.existsById(10L)).thenReturn(true);
        when(householdMemberRepository.existsById(any())).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> householdService.getStats(10L, "2026-04-10", "2026-04-10", 1L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void getStats_householdNotFound_throws404() {
        when(householdRepository.existsById(99L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> householdService.getStats(99L, "2026-04-10", "2026-04-10", 1L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
