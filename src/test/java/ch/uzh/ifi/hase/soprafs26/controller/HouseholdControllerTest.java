package ch.uzh.ifi.hase.soprafs26.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.Household;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdPostDTO;
import ch.uzh.ifi.hase.soprafs26.service.HouseholdService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(HouseholdController.class)
class HouseholdControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HouseholdService householdService;

    @MockitoBean
    private UserRepository userRepository;

    private static final String TEST_TOKEN = "test-token";
    private User authenticatedUser;

    @BeforeEach
    void setUp() {
        authenticatedUser = new User();
        authenticatedUser.setId(1L);
        authenticatedUser.setUsername("testUser");
        authenticatedUser.setToken(TEST_TOKEN);
        authenticatedUser.setStatus(UserStatus.ONLINE);
        given(userRepository.findByToken(TEST_TOKEN)).willReturn(authenticatedUser);
    }

    @Test
    void createHousehold_validInput_returns201() throws Exception {
        Household household = new Household();
        household.setId(10L);
        household.setName("Smith Family");
        household.setInviteCode("ABC123");
        household.setOwnerId(1L);

        given(householdService.createHousehold(eq("Smith Family"), any(User.class))).willReturn(household);

        HouseholdPostDTO dto = new HouseholdPostDTO();
        dto.setName("Smith Family");

        MockHttpServletRequestBuilder request = post("/households")
                .header("Authorization", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(dto));

        mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.householdId", is(10)))
                .andExpect(jsonPath("$.name", is("Smith Family")))
                .andExpect(jsonPath("$.inviteCode", is("ABC123")))
                .andExpect(jsonPath("$.ownerId", is(1)));
    }

    @Test
    void createHousehold_emptyName_returns400() throws Exception {
        given(householdService.createHousehold(eq(""), any(User.class)))
                .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Household name must not be empty."));

        HouseholdPostDTO dto = new HouseholdPostDTO();
        dto.setName("");

        MockHttpServletRequestBuilder request = post("/households")
                .header("Authorization", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(dto));

        mockMvc.perform(request).andExpect(status().isBadRequest());
    }

    @Test
    void createHousehold_nullName_returns400() throws Exception {
        given(householdService.createHousehold(isNull(), any(User.class)))
                .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Household name must not be empty."));

        HouseholdPostDTO dto = new HouseholdPostDTO();

        MockHttpServletRequestBuilder request = post("/households")
                .header("Authorization", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(dto));

        mockMvc.perform(request).andExpect(status().isBadRequest());
    }

    @Test
    void createHousehold_noToken_returns401() throws Exception {
        HouseholdPostDTO dto = new HouseholdPostDTO();
        dto.setName("Smith Family");

        MockHttpServletRequestBuilder request = post("/households")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(dto));

        mockMvc.perform(request).andExpect(status().isUnauthorized());
    }

    private String asJsonString(final Object object) {
        try {
            return new ObjectMapper().writeValueAsString(object);
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("The request body could not be created.%s", e.toString()));
        }
    }
}
