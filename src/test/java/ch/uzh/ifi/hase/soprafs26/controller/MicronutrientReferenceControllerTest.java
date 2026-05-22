package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.MicronutrientRequirementGetDTO;
import ch.uzh.ifi.hase.soprafs26.service.MicronutrientReferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MicronutrientReferenceController.class)
class MicronutrientReferenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MicronutrientReferenceService micronutrientReferenceService;

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

    private MicronutrientRequirementGetDTO buildRequirement(String nutrientKey, String displayName) {
        MicronutrientRequirementGetDTO dto = new MicronutrientRequirementGetDTO();
        dto.setNutrientKey(nutrientKey);
        dto.setDisplayName(displayName);
        dto.setUnit("mg");
        dto.setRecommendedValue(BigDecimal.valueOf(15.0));
        dto.setRecommendedReferenceType("RDA");
        return dto;
    }

    @Test
    void getMicronutrientRequirements_200_returnsRequirements() throws Exception {
        List<MicronutrientRequirementGetDTO> requirements = List.of(
                buildRequirement("iron", "Iron"),
                buildRequirement("calcium", "Calcium")
        );
        given(micronutrientReferenceService.getRequirementsForUser(1L, 1L)).willReturn(requirements);

        mockMvc.perform(get("/users/1/micronutrient-requirements")
                        .header("Authorization", TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].nutrientKey", is("iron")))
                .andExpect(jsonPath("$[1].nutrientKey", is("calcium")));
    }

    @Test
    void getMicronutrientRequirements_200_returnsEmptyList() throws Exception {
        given(micronutrientReferenceService.getRequirementsForUser(1L, 1L)).willReturn(List.of());

        mockMvc.perform(get("/users/1/micronutrient-requirements")
                        .header("Authorization", TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getMicronutrientRequirements_404_whenProfileNotFound() throws Exception {
        given(micronutrientReferenceService.getRequirementsForUser(1L, 1L))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal profile was not found."));

        mockMvc.perform(get("/users/1/micronutrient-requirements")
                        .header("Authorization", TEST_TOKEN))
                .andExpect(status().isNotFound());
    }
}
