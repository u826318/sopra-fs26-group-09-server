package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.LifeStageGroup;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.entity.UserPersonalProfile;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.service.UserPersonalProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserPersonalProfileController.class)
class UserPersonalProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserPersonalProfileService userPersonalProfileService;

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

    private UserPersonalProfile buildProfile() {
        User user = new User();
        user.setId(1L);

        UserPersonalProfile profile = new UserPersonalProfile();
        profile.setId(10L);
        profile.setUser(user);
        profile.setBirthDate(LocalDate.of(1990, 5, 15));
        profile.setLifeStageGroup(LifeStageGroup.FEMALE);
        return profile;
    }

    @Test
    void getPersonalProfile_200_returnsProfile() throws Exception {
        given(userPersonalProfileService.getPersonalProfile(1L, 1L)).willReturn(buildProfile());

        mockMvc.perform(get("/users/1/personal-profile")
                        .header("Authorization", TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(1)))
                .andExpect(jsonPath("$.birthDate", is("1990-05-15")))
                .andExpect(jsonPath("$.lifeStageGroup", is("FEMALE")));
    }

    @Test
    void getPersonalProfile_404_whenNotFound() throws Exception {
        given(userPersonalProfileService.getPersonalProfile(1L, 1L))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal profile was not found."));

        mockMvc.perform(get("/users/1/personal-profile")
                        .header("Authorization", TEST_TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void putPersonalProfile_200_createsOrUpdatesProfile() throws Exception {
        given(userPersonalProfileService.createOrUpdatePersonalProfile(
                eq(1L), eq(1L), any(LocalDate.class), any(LifeStageGroup.class)))
                .willReturn(buildProfile());

        String body = """
                {
                  "birthDate": "1990-05-15",
                  "lifeStageGroup": "FEMALE"
                }
                """;

        mockMvc.perform(put("/users/1/personal-profile")
                        .header("Authorization", TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(1)))
                .andExpect(jsonPath("$.lifeStageGroup", is("FEMALE")));
    }
}
