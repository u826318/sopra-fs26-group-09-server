package ch.uzh.ifi.hase.soprafs26.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.Household;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.entity.HouseholdBudget;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdBudgetPutDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdJoinPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdStatsGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdStatsGetDTO.ComparisonToBudgetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdStatsGetDTO.DailyBreakdownDTO;
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

    // ── POST /households ─────────────────────────────────────────────────────

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

        mockMvc.perform(post("/households")
                .header("Authorization", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(dto)))
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

        mockMvc.perform(post("/households")
                .header("Authorization", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createHousehold_nullName_returns400() throws Exception {
        given(householdService.createHousehold(isNull(), eq(1L)))
                .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Household name must not be empty."));

        HouseholdPostDTO dto = new HouseholdPostDTO();

        mockMvc.perform(post("/households")
                .header("Authorization", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createHousehold_noToken_returns401() throws Exception {
        HouseholdPostDTO dto = new HouseholdPostDTO();
        dto.setName("Smith Family");

        mockMvc.perform(post("/households")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(dto)))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /households/{id}/invite-code ────────────────────────────────────

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

        mockMvc.perform(post("/households/10/invite-code")
                .header("Authorization", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.householdId", is(10)))
                .andExpect(jsonPath("$.inviteCode", is("NEW456")))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void generateInviteCode_householdNotFound_returns404() throws Exception {
        given(householdService.regenerateInviteCode(eq(999L), eq(1L)))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found."));

        mockMvc.perform(post("/households/999/invite-code")
                .header("Authorization", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // ── POST /households/join ────────────────────────────────────────────────

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

        mockMvc.perform(post("/households/join")
                .header("Authorization", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(dto)))
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

        mockMvc.perform(post("/households/join")
                .header("Authorization", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(dto)))
                .andExpect(status().isNotFound());
    }

    // ── GET /households/{id}/budget ──────────────────────────────────────────

    @Test
    void getBudget_member_returns200() throws Exception {
        HouseholdBudget budget = new HouseholdBudget();
        budget.setId(1L);
        budget.setHouseholdId(10L);
        budget.setDailyCalorieTarget(2000.0);
        budget.setUpdatedAt(Instant.now());

        given(householdService.getBudget(eq(10L), eq(1L))).willReturn(budget);

        mockMvc.perform(get("/households/10/budget")
                .header("Authorization", TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetId", is(1)))
                .andExpect(jsonPath("$.householdId", is(10)))
                .andExpect(jsonPath("$.dailyCalorieTarget", is(2000.0)))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void getBudget_noBudgetSet_returns404() throws Exception {
        given(householdService.getBudget(eq(10L), eq(1L)))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No budget set for this household."));

        mockMvc.perform(get("/households/10/budget")
                .header("Authorization", TEST_TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBudget_notMember_returns403() throws Exception {
        given(householdService.getBudget(eq(10L), eq(1L)))
                .willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this household."));

        mockMvc.perform(get("/households/10/budget")
                .header("Authorization", TEST_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void getBudget_noToken_returns401() throws Exception {
        mockMvc.perform(get("/households/10/budget"))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /households/{id}/budget ──────────────────────────────────────────

    @Test
    void updateBudget_owner_returns200() throws Exception {
        HouseholdBudget budget = new HouseholdBudget();
        budget.setId(1L);
        budget.setHouseholdId(10L);
        budget.setDailyCalorieTarget(2000.0);
        budget.setUpdatedAt(Instant.now());

        given(householdService.updateBudget(eq(10L), eq(2000.0), eq(1L))).willReturn(budget);

        HouseholdBudgetPutDTO dto = new HouseholdBudgetPutDTO();
        dto.setDailyCalorieTarget(2000.0);

        mockMvc.perform(put("/households/10/budget")
                .header("Authorization", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetId", is(1)))
                .andExpect(jsonPath("$.dailyCalorieTarget", is(2000.0)));
    }

    @Test
    void updateBudget_nonOwner_returns403() throws Exception {
        given(householdService.updateBudget(eq(10L), eq(2000.0), eq(1L)))
                .willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the household owner can update the budget."));

        HouseholdBudgetPutDTO dto = new HouseholdBudgetPutDTO();
        dto.setDailyCalorieTarget(2000.0);

        mockMvc.perform(put("/households/10/budget")
                .header("Authorization", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateBudget_invalidTarget_returns400() throws Exception {
        given(householdService.updateBudget(eq(10L), eq(0.0), eq(1L)))
                .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Daily calorie target must be greater than 0."));

        HouseholdBudgetPutDTO dto = new HouseholdBudgetPutDTO();
        dto.setDailyCalorieTarget(0.0);

        mockMvc.perform(put("/households/10/budget")
                .header("Authorization", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateBudget_noToken_returns401() throws Exception {
        HouseholdBudgetPutDTO dto = new HouseholdBudgetPutDTO();
        dto.setDailyCalorieTarget(2000.0);

        mockMvc.perform(put("/households/10/budget")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(dto)))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /households/{id}/stats ───────────────────────────────────────────

    @Test
    void getStats_withBudget_returns200() throws Exception {
        HouseholdStatsGetDTO stats = new HouseholdStatsGetDTO();
        stats.setStartDate("2026-04-01");
        stats.setEndDate("2026-04-07");
        stats.setDailyCalorieTarget(2000.0);
        stats.setTotalCaloriesConsumed(12600.0);
        stats.setAverageDailyCalories(1800.0);
        stats.setDailyBreakdown(List.of(
                new DailyBreakdownDTO("2026-04-01", 1800.0),
                new DailyBreakdownDTO("2026-04-02", 1800.0)));
        stats.setComparisonToBudget(new ComparisonToBudgetDTO("UNDER_BUDGET", -200.0, 90.0));

        given(householdService.getStats(eq(10L), eq("2026-04-01"), eq("2026-04-07"), eq(1L))).willReturn(stats);

        mockMvc.perform(get("/households/10/stats")
                .header("Authorization", TEST_TOKEN)
                .param("startDate", "2026-04-01")
                .param("endDate", "2026-04-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate", is("2026-04-01")))
                .andExpect(jsonPath("$.endDate", is("2026-04-07")))
                .andExpect(jsonPath("$.dailyCalorieTarget", is(2000.0)))
                .andExpect(jsonPath("$.totalCaloriesConsumed", is(12600.0)))
                .andExpect(jsonPath("$.averageDailyCalories", is(1800.0)))
                .andExpect(jsonPath("$.dailyBreakdown", hasSize(2)))
                .andExpect(jsonPath("$.comparisonToBudget.status", is("UNDER_BUDGET")))
                .andExpect(jsonPath("$.comparisonToBudget.differenceFromTarget", is(-200.0)))
                .andExpect(jsonPath("$.comparisonToBudget.percentageOfTarget", is(90.0)));
    }

    @Test
    void getStats_notMember_returns403() throws Exception {
        given(householdService.getStats(eq(10L), eq("2026-04-01"), eq("2026-04-07"), eq(1L)))
                .willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this household."));

        mockMvc.perform(get("/households/10/stats")
                .header("Authorization", TEST_TOKEN)
                .param("startDate", "2026-04-01")
                .param("endDate", "2026-04-07"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getStats_householdNotFound_returns404() throws Exception {
        given(householdService.getStats(eq(999L), eq("2026-04-01"), eq("2026-04-07"), eq(1L)))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found."));

        mockMvc.perform(get("/households/999/stats")
                .header("Authorization", TEST_TOKEN)
                .param("startDate", "2026-04-01")
                .param("endDate", "2026-04-07"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getStats_invalidDate_returns400() throws Exception {
        given(householdService.getStats(eq(10L), eq("bad"), eq("2026-04-07"), eq(1L)))
                .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format. Expected YYYY-MM-DD."));

        mockMvc.perform(get("/households/10/stats")
                .header("Authorization", TEST_TOKEN)
                .param("startDate", "bad")
                .param("endDate", "2026-04-07"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getStats_noToken_returns401() throws Exception {
        mockMvc.perform(get("/households/10/stats")
                .param("startDate", "2026-04-01")
                .param("endDate", "2026-04-07"))
                .andExpect(status().isUnauthorized());
    }
        @Test
    void generateInviteCode_nonOwner_returns403() throws Exception {
        given(householdService.regenerateInviteCode(eq(10L), eq(1L)))
                .willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Only the household owner can generate invite codes."));

        MockHttpServletRequestBuilder request = post("/households/10/invite-code")
                .header("Authorization", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(request).andExpect(status().isForbidden());
    }

    @Test
    void joinHousehold_expiredInviteCode_returns410() throws Exception {
        given(householdService.joinHouseholdByInviteCode(eq("EXPIRED"), eq(1L)))
                .willThrow(new ResponseStatusException(HttpStatus.GONE,
                        "Invite code has expired. Please request a new code."));

        HouseholdJoinPostDTO dto = new HouseholdJoinPostDTO();
        dto.setInviteCode("EXPIRED");

        MockHttpServletRequestBuilder request = post("/households/join")
                .header("Authorization", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(dto));

        mockMvc.perform(request).andExpect(status().isGone());
    }

    @Test
    void joinHousehold_emptyInviteCode_returns400() throws Exception {
        given(householdService.joinHouseholdByInviteCode(eq(""), eq(1L)))
                .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invite code must not be empty."));

        HouseholdJoinPostDTO dto = new HouseholdJoinPostDTO();
        dto.setInviteCode("");

        MockHttpServletRequestBuilder request = post("/households/join")
                .header("Authorization", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(dto));

        mockMvc.perform(request).andExpect(status().isBadRequest());
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
