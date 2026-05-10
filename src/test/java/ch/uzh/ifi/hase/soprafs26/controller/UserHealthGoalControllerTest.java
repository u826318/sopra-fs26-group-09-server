package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.entity.UserHealthGoal;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.service.UserHealthGoalService;
import ch.uzh.ifi.hase.soprafs26.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserHealthGoalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserHealthGoalService userHealthGoalService;

    @MockitoBean
    private UserRepository userRepository;

    private static final String TEST_TOKEN = "test-token";

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setToken(TEST_TOKEN);
        user.setStatus(UserStatus.ONLINE);
        given(userRepository.findByToken(TEST_TOKEN)).willReturn(user);
    }

    @Test
    void getHealthGoal_200_returnsGoal() throws Exception {
        UserHealthGoal goal = buildGoal(1L, "LOSE_WEIGHT", 0.5, 1592.89);
        given(userHealthGoalService.getGoal(1L)).willReturn(goal);

        mockMvc.perform(get("/users/1/health-goal")
                .header("Authorization", TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalType", is("LOSE_WEIGHT")))
                .andExpect(jsonPath("$.recommendedDailyCalories", is(1592.89)))
                .andExpect(jsonPath("$.age", is(28)));
    }

    @Test
    void getHealthGoal_404_whenNotSet() throws Exception {
        given(userHealthGoalService.getGoal(1L))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Health goal not set."));

        mockMvc.perform(get("/users/1/health-goal")
                .header("Authorization", TEST_TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void getHealthGoal_403_whenAccessingOtherUser() throws Exception {
        mockMvc.perform(get("/users/99/health-goal")
                .header("Authorization", TEST_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void putHealthGoal_200_createsGoal() throws Exception {
        UserHealthGoal saved = buildGoal(1L, "LOSE_WEIGHT", 0.5, 1592.89);
        given(userHealthGoalService.upsertGoal(eq(1L), any())).willReturn(saved);

        // targetRate removed from PUT body; backend derives it from targetWeight and weeksToGoal
        String body = """
                {
                  "goalType": "LOSE_WEIGHT",
                  "targetWeight": 57.5,
                  "weeksToGoal": 9,
                  "age": 28,
                  "sex": "FEMALE",
                  "height": 165.0,
                  "weight": 62.0,
                  "activityLevel": "MODERATE"
                }
                """;

        mockMvc.perform(put("/users/1/health-goal")
                .header("Authorization", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedDailyCalories", is(1592.89)));
    }

    @Test
    void putHealthGoal_403_whenAccessingOtherUser() throws Exception {
        String body = """
                {
                  "goalType": "MAINTAIN",
                  "age": 28,
                  "sex": "FEMALE",
                  "height": 165.0,
                  "weight": 62.0,
                  "activityLevel": "MODERATE"
                }
                """;

        mockMvc.perform(put("/users/99/health-goal")
                .header("Authorization", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isForbidden());
    }

    private UserHealthGoal buildGoal(Long userId, String goalType, Double targetRate, double kcal) {
        UserHealthGoal goal = new UserHealthGoal();
        goal.setGoalId(1L);
        goal.setUserId(userId);
        goal.setGoalType(goalType);
        goal.setTargetRate(targetRate);
        // targetWeight and weeksToGoal stored so the frontend can restore the form on reload
        goal.setTargetWeight(targetRate != null ? 57.5 : null);
        goal.setWeeksToGoal(targetRate != null ? 9 : null);
        goal.setAge(28);
        goal.setSex("FEMALE");
        goal.setHeight(165.0);
        goal.setWeight(62.0);
        goal.setActivityLevel("MODERATE");
        goal.setRecommendedDailyCalories(kcal);
        goal.setUpdatedAt(Instant.now());
        return goal;
    }
}
