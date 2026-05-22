package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.DailyNutrientIntake;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.service.DailyNutrientIntakeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserDailyNutrientIntakeController.class)
class UserDailyNutrientIntakeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DailyNutrientIntakeService dailyNutrientIntakeService;

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

    private DailyNutrientIntake buildIntake(Long userId, LocalDate date) {
        DailyNutrientIntake intake = new DailyNutrientIntake();
        intake.setId(5L);
        intake.setUserId(userId);
        intake.setIntakeDate(date);
        return intake;
    }

    @Test
    void getDailyNutrientIntake_200_withoutDate() throws Exception {
        DailyNutrientIntake intake = buildIntake(1L, LocalDate.of(2026, 5, 22));
        given(dailyNutrientIntakeService.getDailyIntakeOrEmpty(eq(1L), any()))
                .willReturn(intake);

        mockMvc.perform(get("/users/1/daily-nutrient-intake")
                        .header("Authorization", TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(1)));
    }

    @Test
    void getDailyNutrientIntake_200_withDate() throws Exception {
        LocalDate date = LocalDate.of(2026, 5, 10);
        DailyNutrientIntake intake = buildIntake(1L, date);
        given(dailyNutrientIntakeService.getDailyIntakeOrEmpty(1L, date))
                .willReturn(intake);

        mockMvc.perform(get("/users/1/daily-nutrient-intake")
                        .param("date", "2026-05-10")
                        .header("Authorization", TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(1)))
                .andExpect(jsonPath("$.intakeDate", is("2026-05-10")));
    }

    @Test
    void getDailyNutrientIntake_403_whenAccessingOtherUser() throws Exception {
        mockMvc.perform(get("/users/2/daily-nutrient-intake")
                        .header("Authorization", TEST_TOKEN))
                .andExpect(status().isForbidden());
    }
}
