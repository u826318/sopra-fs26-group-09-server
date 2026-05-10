package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.entity.UserHealthGoal;
import ch.uzh.ifi.hase.soprafs26.repository.UserHealthGoalRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserHealthGoalPutDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class UserHealthGoalServiceTest {

    @Mock
    private UserHealthGoalRepository repository;

    @InjectMocks
    private UserHealthGoalService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ── MAINTAIN / GAIN_MUSCLE regression tests ───────────────────────────────

    @Test
    void calculate_maleMaintain() {
        UserHealthGoalPutDTO dto = buildDto("MALE", 30, 180.0, 80.0, "ACTIVE", "MAINTAIN", null, null);
        // BMR = 10*80 + 6.25*180 - 5*30 + 5 = 1780.0
        // TDEE = 1780.0 * 1.725 = 3070.5
        assertEquals(3070.5, UserHealthGoalService.calculate(dto), 0.1);
    }

    @Test
    void calculate_gainMuscle_addThreeHundred() {
        UserHealthGoalPutDTO dto = buildDto("MALE", 25, 175.0, 70.0, "LIGHT", "GAIN_MUSCLE", null, null);
        // BMR = 10*70 + 6.25*175 - 5*25 + 5 = 1673.75
        // TDEE = 1673.75 * 1.375 = 2301.40625; +300 = 2601.40625
        assertEquals(2601.4, UserHealthGoalService.calculate(dto), 0.1);
    }

    @Test
    void calculate_otherSex_averageOfMaleAndFemale() {
        UserHealthGoalPutDTO dto = buildDto("OTHER", 28, 165.0, 62.0, "SEDENTARY", "MAINTAIN", null, null);
        // OTHER BMR = average of female (-161) and male (+5) offsets = base - 78
        double base = 10 * 62 + 6.25 * 165 - 5 * 28; // = 1511.25
        double expected = (base - 78.0) * 1.2;        // = 1433.25 * 1.2 = 1719.9
        assertEquals(expected, UserHealthGoalService.calculate(dto), 0.1);
    }

    // ── Hall model: LOSE_WEIGHT ───────────────────────────────────────────────

    // Normal Hall path — no cap, no floor triggered.
    // rate = 10kg / 30 weeks = 0.333 kg/week
    // W_avg = (62 + 52) / 2 = 57 kg
    // BMR_avg(57kg,165cm,28y,FEMALE) = 10*57 + 6.25*165 - 5*28 - 161 = 1300.25
    // TDEE_avg = 1300.25 * 1.55 = 2015.39; adapted (×0.90) = 1813.85
    // dailyDeficit = (10/30) * 7700 / 7 = 366.67
    // raw = 1813.85 - 366.67 = 1447.18
    // TDEE0 = (10*62+6.25*165-5*28-161)*1.55 = 1350.25*1.55 = 2092.89
    // 35% cap min = 2092.89 * 0.65 = 1360.38; floor = 1200
    // result = max(1447.18, 1360.38, 1200) = 1447.18
    @Test
    void calculate_loseWeight_normalHallPath() {
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 28, 165.0, 62.0, "MODERATE", "LOSE_WEIGHT", 52.0, 30);
        assertEquals(1447.18, UserHealthGoalService.calculate(dto), 0.5);
    }

    // 35% deficit cap triggered.
    // rate = 10kg / 20 weeks = 0.5 kg/week; dailyDeficit = 550
    // raw Hall = 1813.85 - 550 = 1263.85 < cap 1360.38 → cap wins
    // result = max(1263.85, 1360.38, 1200) = 1360.38
    @Test
    void calculate_loseWeight_capTriggered() {
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 28, 165.0, 62.0, "MODERATE", "LOSE_WEIGHT", 52.0, 20);
        assertEquals(1360.38, UserHealthGoalService.calculate(dto), 0.5);
    }

    // 1200 kcal floor triggered for small sedentary female.
    // rate = 5kg / 8 weeks = 0.625 kg/week
    // W_avg = 42.5 kg; BMR_avg = 10*42.5+6.25*150-5*23-161 = 1086.5
    // TDEE_avg = 1086.5*1.2 = 1303.8; adapted = 1173.42
    // dailyDeficit = 0.625 * 7700/7 = 687.5; raw = 485.92
    // TDEE0 = (10*45+6.25*150-5*23-161)*1.2 = 1111.5*1.2 = 1333.8
    // cap min = 1333.8 * 0.65 = 866.97; floor = 1200
    // result = max(485.92, 866.97, 1200) = 1200
    @Test
    void calculate_loseWeight_floorTriggered() {
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 23, 150.0, 45.0, "SEDENTARY", "LOSE_WEIGHT", 40.0, 8);
        assertEquals(1200.0, UserHealthGoalService.calculate(dto), 0.1);
    }

    // Validation: targetWeight null for LOSE_WEIGHT must throw 400
    @Test
    void calculate_loseWeight_nullTargetWeight_throws400() {
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 28, 165.0, 62.0, "MODERATE", "LOSE_WEIGHT", null, 20);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> UserHealthGoalService.calculate(dto));
        assertEquals(400, ex.getStatusCode().value());
    }

    // Validation: weeksToGoal null for LOSE_WEIGHT must throw 400
    @Test
    void calculate_loseWeight_nullWeeksToGoal_throws400() {
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 28, 165.0, 62.0, "MODERATE", "LOSE_WEIGHT", 52.0, null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> UserHealthGoalService.calculate(dto));
        assertEquals(400, ex.getStatusCode().value());
    }

    // ── upsertGoal integration stubs ─────────────────────────────────────────

    @Test
    void getGoal_notFound_throws404() {
        when(repository.findByUserId(99L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getGoal(99L));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void getGoal_found_returnsGoal() {
        UserHealthGoal goal = new UserHealthGoal();
        goal.setUserId(1L);
        goal.setGoalType("MAINTAIN");
        when(repository.findByUserId(1L)).thenReturn(Optional.of(goal));

        UserHealthGoal result = service.getGoal(1L);
        assertEquals("MAINTAIN", result.getGoalType());
    }

    @Test
    void upsertGoal_createsNewWhenNotExists() {
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 28, 165.0, 62.0, "MODERATE", "MAINTAIN", null, null);
        when(repository.findByUserId(1L)).thenReturn(Optional.empty());

        UserHealthGoal saved = new UserHealthGoal();
        saved.setGoalId(1L);
        saved.setUserId(1L);
        saved.setGoalType("MAINTAIN");
        saved.setRecommendedDailyCalories(2092.89);
        when(repository.save(any())).thenReturn(saved);

        UserHealthGoal result = service.upsertGoal(1L, dto);
        assertEquals(1L, result.getUserId());
        assertEquals("MAINTAIN", result.getGoalType());
    }

    @Test
    void upsertGoal_updatesExisting() {
        UserHealthGoalPutDTO dto = buildDto("MALE", 30, 180.0, 80.0, "ACTIVE", "MAINTAIN", null, null);

        UserHealthGoal existing = new UserHealthGoal();
        existing.setGoalId(5L);
        existing.setUserId(2L);
        existing.setGoalType("LOSE_WEIGHT");
        when(repository.findByUserId(2L)).thenReturn(Optional.of(existing));

        UserHealthGoal updated = new UserHealthGoal();
        updated.setGoalId(5L);
        updated.setUserId(2L);
        updated.setGoalType("MAINTAIN");
        updated.setRecommendedDailyCalories(3070.5);
        when(repository.save(any())).thenReturn(updated);

        UserHealthGoal result = service.upsertGoal(2L, dto);
        assertEquals("MAINTAIN", result.getGoalType());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private UserHealthGoalPutDTO buildDto(String sex, int age, double height, double weight,
                                          String activity, String goalType,
                                          Double targetWeight, Integer weeksToGoal) {
        UserHealthGoalPutDTO dto = new UserHealthGoalPutDTO();
        dto.setSex(sex);
        dto.setAge(age);
        dto.setHeight(height);
        dto.setWeight(weight);
        dto.setActivityLevel(activity);
        dto.setGoalType(goalType);
        dto.setTargetWeight(targetWeight);
        dto.setWeeksToGoal(weeksToGoal);
        return dto;
    }
}
