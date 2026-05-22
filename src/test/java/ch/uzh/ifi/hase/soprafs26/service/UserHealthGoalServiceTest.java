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

    // Normal Hall path — 25% cap not triggered (rate slow enough).
    // rate = 10kg / 50 weeks = 0.2 kg/week
    // W_avg = (62 + 52) / 2 = 57 kg
    // BMR_avg(57kg,165cm,28y,FEMALE) = 10*57 + 6.25*165 - 5*28 - 161 = 1300.25
    // TDEE_avg = 1300.25 * 1.55 = 2015.39; adapted (×0.90) = 1813.85
    // dailyDeficit = 0.2 * 7700 / 7 = 220
    // raw = 1813.85 - 220 = 1593.85
    // TDEE0 = 1350.25 * 1.55 = 2092.89
    // 25% cap min = 2092.89 * 0.75 = 1569.67; raw 1593.85 > 1569.67 → raw wins
    // result = max(1593.85, 1569.67, 1200) = 1593.85
    @Test
    void calculate_loseWeight_normalHallPath() {
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 28, 165.0, 62.0, "MODERATE", "LOSE_WEIGHT", 52.0, 50);
        assertEquals(1593.85, UserHealthGoalService.calculate(dto), 0.5);
    }

    // Adult 25% deficit cap triggered.
    // rate = 10kg / 20 weeks = 0.5 kg/week; dailyDeficit = 550
    // raw Hall = 1813.85 - 550 = 1263.85
    // TDEE0 = 2092.89; 25% cap min = 2092.89 * 0.75 = 1569.67 → cap wins
    // result = max(1263.85, 1569.67, 1200) = 1569.67
    @Test
    void calculate_loseWeight_adultCapTriggered() {
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 28, 165.0, 62.0, "MODERATE", "LOSE_WEIGHT", 52.0, 20);
        assertEquals(1569.67, UserHealthGoalService.calculate(dto), 0.5);
    }

    // Child (< 13): 10% max deficit cap.
    // Female, age 10, height 140cm, weight 40kg, MODERATE, target 35kg, 20 weeks
    // rate = 0.25 kg/wk
    // TDEE0 = (10*40+6.25*140-5*10-161)*1.55 = 1064*1.55 = 1649.2
    // W_avg = 37.5; BMR_avg = 1039; TDEE_avg = 1610.45; adapted = 1449.4
    // dailyDeficit = 275; raw = 1449.4 - 275 = 1174.4
    // 10% cap min = 1649.2 * 0.90 = 1484.28; floor = 1000
    // result = max(1174.4, 1484.28, 1000) = 1484.28
    @Test
    void calculate_loseWeight_childCapTriggered() {
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 10, 140.0, 40.0, "MODERATE", "LOSE_WEIGHT", 35.0, 20);
        assertEquals(1484.28, UserHealthGoalService.calculate(dto), 0.5);
    }

    // Child where absolute floor (1000) would exceed TDEE: floor must be ignored.
    // Female, age 6, weight 20kg, height 115cm, SEDENTARY, target 18kg, 8 weeks
    // rate = 0.25 kg/wk
    // TDEE0 = (10*20+6.25*115-5*6-161)*1.2 = 727.75*1.2 = 873.3  → floor 1000 > TDEE0 → invalidated
    // W_avg = 19; BMR_avg = 717.75; TDEE_avg = 861.3; adapted = 775.17
    // dailyDeficit = 275; raw = 775.17 - 275 = 500.17
    // 10% cap min = 873.3 * 0.90 = 785.97
    // result = max(500.17, 785.97, 0) = 785.97
    @Test
    void calculate_loseWeight_childFloorAboveTdeeIsIgnored() {
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 6, 115.0, 20.0, "SEDENTARY", "LOSE_WEIGHT", 18.0, 8);
        assertEquals(785.97, UserHealthGoalService.calculate(dto), 0.5);
    }

    // Adolescent (13-17): 15% max deficit cap.
    // Female, age 15, height 165cm, weight 70kg, MODERATE, target 60kg, 20 weeks
    // rate = 0.5 kg/wk
    // TDEE0 = (10*70+6.25*165-5*15-161)*1.55 = 1495.25*1.55 = 2317.64
    // W_avg = 65; BMR_avg = 1445.25; TDEE_avg = 2240.14; adapted = 2016.12
    // dailyDeficit = 550; raw = 2016.12 - 550 = 1466.12
    // 15% cap min = 2317.64 * 0.85 = 1969.99; floor = 1300
    // result = max(1466.12, 1969.99, 1300) = 1969.99
    @Test
    void calculate_loseWeight_adolescentCapTriggered() {
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 15, 165.0, 70.0, "MODERATE", "LOSE_WEIGHT", 60.0, 20);
        assertEquals(1969.99, UserHealthGoalService.calculate(dto), 0.5);
    }

    // Senior (>= 65): 15% max deficit cap.
    // Female, age 68, height 162cm, weight 70kg, LIGHT, target 65kg, 20 weeks
    // rate = 0.25 kg/wk
    // TDEE0 = (10*70+6.25*162-5*68-161)*1.375 = 1211.5*1.375 = 1665.81
    // W_avg = 67.5; BMR_avg = 1186.5; TDEE_avg = 1631.44; adapted = 1468.29
    // dailyDeficit = 275; raw = 1468.29 - 275 = 1193.29
    // 15% cap min = 1665.81 * 0.85 = 1415.94; floor = 1400
    // result = max(1193.29, 1415.94, 1400) = 1415.94
    @Test
    void calculate_loseWeight_seniorCapTriggered() {
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 68, 162.0, 70.0, "LIGHT", "LOSE_WEIGHT", 65.0, 20);
        assertEquals(1415.94, UserHealthGoalService.calculate(dto), 0.5);
    }

    // 1400 kcal floor triggered for sedentary adult female (TDEE0 > 1400 so floor is valid).
    // Female, age 25, height 165cm, weight 48kg, SEDENTARY, target 40kg, 20 weeks
    // rate = 0.4 kg/week
    // TDEE0 = (10*48+6.25*165-5*25-161)*1.2 = 1225.25*1.2 = 1470.3  → floor 1400 < 1470.3 → valid
    // W_avg = 44; BMR_avg = 1185.25; TDEE_avg = 1422.3; adapted = 1280.07
    // dailyDeficit = 440; raw = 1280.07 - 440 = 840.07
    // 25% cap min = 1470.3 * 0.75 = 1102.73
    // result = max(840.07, 1102.73, 1400) = 1400
    @Test
    void calculate_loseWeight_floorTriggered() {
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 25, 165.0, 48.0, "SEDENTARY", "LOSE_WEIGHT", 40.0, 20);
        assertEquals(1400.0, UserHealthGoalService.calculate(dto), 0.1);
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

    // Validation: weeksToGoal == 0 would cause division by zero → must throw 400
    @Test
    void calculate_loseWeight_zeroWeeksToGoal_throws400() {
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 28, 165.0, 62.0, "MODERATE", "LOSE_WEIGHT", 52.0, 0);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> UserHealthGoalService.calculate(dto));
        assertEquals(400, ex.getStatusCode().value());
    }

    // Validation: targetWeight >= current weight is nonsensical for LOSE_WEIGHT → must throw 400
    @Test
    void calculate_loseWeight_targetWeightNotLessThanCurrentWeight_throws400() {
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 28, 165.0, 62.0, "MODERATE", "LOSE_WEIGHT", 62.0, 20);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> UserHealthGoalService.calculate(dto));
        assertEquals(400, ex.getStatusCode().value());
    }

    // Validation: negative weeksToGoal is also rejected (condition is <= 0)
    @Test
    void calculate_loseWeight_negativeWeeksToGoal_throws400() {
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 28, 165.0, 62.0, "MODERATE", "LOSE_WEIGHT", 52.0, -5);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> UserHealthGoalService.calculate(dto));
        assertEquals(400, ex.getStatusCode().value());
    }

    // 1600 kcal floor triggered for sedentary adult male (TDEE0 > 1600 so floor is valid).
    // Male, age 30, height 170cm, weight 55kg, SEDENTARY, target 48kg, 20 weeks
    // rate = 0.35 kg/week
    // TDEE0 = (10*55+6.25*170-5*30+5)*1.2 = 1467.5*1.2 = 1761.0  → floor 1600 < 1761 → valid
    // W_avg = 51.5; BMR_avg = 1432.5; TDEE_avg = 1719.0; adapted = 1547.1
    // dailyDeficit = 385; raw = 1547.1 - 385 = 1162.1
    // 25% cap min = 1761.0 * 0.75 = 1320.75
    // result = max(1162.1, 1320.75, 1600) = 1600
    @Test
    void calculate_loseWeight_maleFloorTriggered() {
        UserHealthGoalPutDTO dto = buildDto("MALE", 30, 170.0, 55.0, "SEDENTARY", "LOSE_WEIGHT", 48.0, 20);
        assertEquals(1600.0, UserHealthGoalService.calculate(dto), 0.1);
    }

    // Invalid activityLevel must throw 400
    @Test
    void calculate_invalidActivityLevel_throws400() {
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 28, 165.0, 62.0, "COUCH_POTATO", "MAINTAIN", null, null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> UserHealthGoalService.calculate(dto));
        assertEquals(400, ex.getStatusCode().value());
    }

    // Invalid goalType must throw 400
    @Test
    void calculate_invalidGoalType_throws400() {
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 28, 165.0, 62.0, "MODERATE", "GET_JACKED", null, null);
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

    // upsertGoal with LOSE_WEIGHT: verifies targetRate is derived from targetWeight/weeksToGoal
    @Test
    void upsertGoal_loseWeight_derivesTargetRateCorrectly() {
        // rate = (62 - 52) / 20 = 0.5 kg/week
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 28, 165.0, 62.0, "MODERATE", "LOSE_WEIGHT", 52.0, 20);
        when(repository.findByUserId(3L)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserHealthGoal result = service.upsertGoal(3L, dto);
        assertEquals(0.5, result.getTargetRate(), 0.001);
        assertEquals(52.0, result.getTargetWeight(), 0.001);
        assertEquals(20, result.getWeeksToGoal());
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
