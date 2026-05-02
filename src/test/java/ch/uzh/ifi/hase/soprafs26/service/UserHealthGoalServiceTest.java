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

    @Test
    void calculate_femaleModerateLosingHalfKgPerWeek() {
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 28, 165.0, 62.0, "MODERATE", "LOSE_WEIGHT", 0.5);
        // BMR = 10*62 + 6.25*165 - 5*28 - 161 = 1350.25
        // TDEE = 1350.25 * 1.55 = 2092.8875
        // LOSE_WEIGHT = 2092.8875 - 500 = 1592.8875
        assertEquals(1592.89, UserHealthGoalService.calculate(dto), 0.1);
    }

    @Test
    void calculate_maleMaintain() {
        UserHealthGoalPutDTO dto = buildDto("MALE", 30, 180.0, 80.0, "ACTIVE", "MAINTAIN", null);
        // BMR = 10*80 + 6.25*180 - 5*30 + 5 = 1780.0
        // TDEE = 1780.0 * 1.725 = 3070.5
        assertEquals(3070.5, UserHealthGoalService.calculate(dto), 0.1);
    }

    @Test
    void calculate_gainMuscle_addThreeHundred() {
        UserHealthGoalPutDTO dto = buildDto("MALE", 25, 175.0, 70.0, "LIGHT", "GAIN_MUSCLE", null);
        // BMR = 10*70 + 6.25*175 - 5*25 + 5 = 1673.75
        // TDEE = 1673.75 * 1.375 = 2301.40625
        // GAIN_MUSCLE = 2301.40625 + 300 = 2601.40625
        assertEquals(2601.4, UserHealthGoalService.calculate(dto), 0.1);
    }

    @Test
    void calculate_otherSex_averageOfMaleAndFemale() {
        UserHealthGoalPutDTO dto = buildDto("OTHER", 28, 165.0, 62.0, "SEDENTARY", "MAINTAIN", null);
        double female = 10 * 62 + 6.25 * 165 - 5 * 28 - 161;
        double male = 10 * 62 + 6.25 * 165 - 5 * 28 + 5;
        double expected = ((female + male) / 2.0) * 1.2;
        assertEquals(expected, UserHealthGoalService.calculate(dto), 0.1);
    }

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
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 28, 165.0, 62.0, "MODERATE", "MAINTAIN", null);
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
        UserHealthGoalPutDTO dto = buildDto("MALE", 30, 180.0, 80.0, "ACTIVE", "MAINTAIN", null);

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

    private UserHealthGoalPutDTO buildDto(String sex, int age, double height, double weight,
                                          String activity, String goalType, Double targetRate) {
        UserHealthGoalPutDTO dto = new UserHealthGoalPutDTO();
        dto.setSex(sex);
        dto.setAge(age);
        dto.setHeight(height);
        dto.setWeight(weight);
        dto.setActivityLevel(activity);
        dto.setGoalType(goalType);
        dto.setTargetRate(targetRate);
        return dto;
    }
}
