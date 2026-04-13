package ch.uzh.ifi.hase.soprafs26.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

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
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdJoinPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdPostDTO;
import ch.uzh.ifi.hase.soprafs26.service.HouseholdService;
import ch.uzh.ifi.hase.soprafs26.service.HouseholdService.HouseholdAccess;

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
    void getHouseholds_returnsList() throws Exception {
        Household ownerHousehold = new Household();
        ownerHousehold.setId(10L);
        ownerHousehold.setName("Owner House");
        ownerHousehold.setInviteCode("OWN123");
        ownerHousehold.setOwnerId(1L);

        Household memberHousehold = new Household();
        memberHousehold.setId(20L);
        memberHousehold.setName("Member House");
        memberHousehold.setInviteCode("MEM123");
        memberHousehold.setOwnerId(2L);

        given(householdService.getHouseholdsForUser(eq(1L))).willReturn(List.of(
                new HouseholdAccess(ownerHousehold, "owner"),
                new HouseholdAccess(memberHousehold, "member")
        ));

        mockMvc.perform(get("/households").header("Authorization", TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].householdId", is(10)))
                .andExpect(jsonPath("$[0].role", is("owner")))
                .andExpect(jsonPath("$[1].householdId", is(20)))
                .andExpect(jsonPath("$[1].role", is("member")));
    }

    @Test
    void getHousehold_returnsSingleHousehold() throws Exception {
        Household household = new Household();
        household.setId(10L);
        household.setName("Smith Family");
        household.setInviteCode("ABC123");
        household.setOwnerId(1L);

        given(householdService.getHouseholdForUser(eq(10L), eq(1L)))
                .willReturn(new HouseholdAccess(household, "owner"));

        mockMvc.perform(get("/households/10").header("Authorization", TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.householdId", is(10)))
                .andExpect(jsonPath("$.name", is("Smith Family")))
                .andExpect(jsonPath("$.role", is("owner")));
    }

    @Test
    void createHousehold_validInput_returns201() throws Exception {
        Household household = new Household();
        household.setId(10L);
        household.setName("Smith Family");
        household.setInviteCode("ABC123");
        household.setOwnerId(1L);

        given(householdService.createHousehold(eq("Smith Family"), eq(1L))).willReturn(household);

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
                .andExpect(jsonPath("$.ownerId", is(1)))
                .andExpect(jsonPath("$.role", is("owner")));
    }

    @Test
    void createHousehold_emptyName_returns400() throws Exception {
        given(householdService.createHousehold(eq(""), eq(1L)))
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
        given(householdService.createHousehold(isNull(), eq(1L)))
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

    @Test
    void deleteHousehold_returns204() throws Exception {
        mockMvc.perform(delete("/households/10").header("Authorization", TEST_TOKEN))
                .andExpect(status().isNoContent());
    }

    @Test
    void generateInviteCode_owner_returns200() throws Exception {
        Household household = new Household();
        household.setId(10L);
        household.setInviteCode("NEW456");
        household.setInviteCodeExpiresAt(Instant.now().plusSeconds(60));

        given(householdService.regenerateInviteCode(eq(10L), eq(1L))).willReturn(household);

        MockHttpServletRequestBuilder request = post("/households/10/invite-code")
                .header("Authorization", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.householdId", is(10)))
                .andExpect(jsonPath("$.inviteCode", is("NEW456")))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void generateInviteCode_householdNotFound_returns404() throws Exception {
        given(householdService.regenerateInviteCode(eq(999L), eq(1L)))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found."));

        MockHttpServletRequestBuilder request = post("/households/999/invite-code")
                .header("Authorization", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(request).andExpect(status().isNotFound());
    }

    @Test
    void joinHousehold_validInviteCode_returns200() throws Exception {
        Household household = new Household();
        household.setId(10L);
        household.setName("Smith Family");
        household.setInviteCode("ABC123");
        household.setOwnerId(1L);

        given(householdService.joinHouseholdByInviteCode(eq("ABC123"), eq(1L))).willReturn(household);

        HouseholdJoinPostDTO dto = new HouseholdJoinPostDTO();
        dto.setInviteCode("ABC123");

        MockHttpServletRequestBuilder request = post("/households/join")
                .header("Authorization", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(dto));

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.householdId", is(10)))
                .andExpect(jsonPath("$.name", is("Smith Family")))
                .andExpect(jsonPath("$.inviteCode", is("ABC123")))
                .andExpect(jsonPath("$.ownerId", is(1)))
                .andExpect(jsonPath("$.role", is("member")));
    }

    @Test
    void joinHousehold_invalidInviteCode_returns404() throws Exception {
        given(householdService.joinHouseholdByInviteCode(eq("INVALID"), eq(1L)))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite code is invalid."));

        HouseholdJoinPostDTO dto = new HouseholdJoinPostDTO();
        dto.setInviteCode("INVALID");

        MockHttpServletRequestBuilder request = post("/households/join")
                .header("Authorization", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(dto));

        mockMvc.perform(request).andExpect(status().isNotFound());
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
