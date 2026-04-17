package ch.uzh.ifi.hase.soprafs26.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ch.uzh.ifi.hase.soprafs26.entity.Household;
import ch.uzh.ifi.hase.soprafs26.entity.HouseholdBudget;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdBudgetGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdBudgetPutDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdInviteCodeGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdJoinPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ConsumptionLogGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdStatsGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.mapper.DTOMapper;
import ch.uzh.ifi.hase.soprafs26.service.HouseholdService;
import ch.uzh.ifi.hase.soprafs26.service.HouseholdService.HouseholdAccess;

@RestController
public class HouseholdController {

    private final HouseholdService householdService;

    HouseholdController(HouseholdService householdService) {
        this.householdService = householdService;
    }

    @GetMapping("/households")
    @ResponseStatus(HttpStatus.OK)
    public List<HouseholdGetDTO> getHouseholds(
            @RequestAttribute("authenticatedUserId") Long authenticatedUserId) {
        return householdService.getHouseholdsForUser(authenticatedUserId).stream()
                .map(this::toHouseholdGetDTO)
                .toList();
    }

    @GetMapping("/households/{householdId}")
    @ResponseStatus(HttpStatus.OK)
    public HouseholdGetDTO getHousehold(
            @RequestAttribute("authenticatedUserId") Long authenticatedUserId,
            @PathVariable Long householdId) {
        HouseholdAccess householdAccess = householdService.getHouseholdForUser(householdId, authenticatedUserId);
        return toHouseholdGetDTO(householdAccess);
    }

    @PostMapping("/households")
    @ResponseStatus(HttpStatus.CREATED)
    public HouseholdGetDTO createHousehold(
            @RequestAttribute("authenticatedUserId") Long authenticatedUserId,
            @RequestBody HouseholdPostDTO householdPostDTO) {

        Household created = householdService.createHousehold(householdPostDTO.getName(), authenticatedUserId);
        return toHouseholdGetDTO(new HouseholdAccess(created, "owner"));
    }

    @DeleteMapping("/households/{householdId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHousehold(
            @RequestAttribute("authenticatedUserId") Long authenticatedUserId,
            @PathVariable Long householdId) {
        householdService.deleteHousehold(householdId, authenticatedUserId);
    }

    @PostMapping("/households/{householdId}/invite-code")
    @ResponseStatus(HttpStatus.OK)
    public HouseholdInviteCodeGetDTO generateInviteCode(
            @RequestAttribute("authenticatedUserId") Long authenticatedUserId,
            @PathVariable Long householdId) {

        Household household = householdService.regenerateInviteCode(householdId, authenticatedUserId);
        HouseholdInviteCodeGetDTO dto = new HouseholdInviteCodeGetDTO();
        dto.setHouseholdId(household.getId());
        dto.setInviteCode(household.getInviteCode());
        dto.setExpiresAt(household.getInviteCodeExpiresAt());
        return dto;
    }

    @PostMapping("/households/join")
    @ResponseStatus(HttpStatus.OK)
    public HouseholdGetDTO joinHousehold(
            @RequestAttribute("authenticatedUserId") Long authenticatedUserId,
            @RequestBody HouseholdJoinPostDTO joinPostDTO) {

        Household household = householdService.joinHouseholdByInviteCode(joinPostDTO.getInviteCode(), authenticatedUserId);
        return toHouseholdGetDTO(new HouseholdAccess(household, "member"));
    }

    private HouseholdGetDTO toHouseholdGetDTO(HouseholdAccess householdAccess) {
        HouseholdGetDTO dto = DTOMapper.INSTANCE.convertEntityToHouseholdGetDTO(householdAccess.household());
        dto.setRole(householdAccess.role());
        return dto;
    }

    @GetMapping("/households/{householdId}/budget")
    @ResponseStatus(HttpStatus.OK)
    public HouseholdBudgetGetDTO getBudget(
            @RequestAttribute("authenticatedUserId") Long authenticatedUserId,
            @PathVariable Long householdId) {

        HouseholdBudget budget = householdService.getBudget(householdId, authenticatedUserId);
        HouseholdBudgetGetDTO dto = new HouseholdBudgetGetDTO();
        dto.setBudgetId(budget.getId());
        dto.setHouseholdId(budget.getHouseholdId());
        dto.setDailyCalorieTarget(budget.getDailyCalorieTarget());
        dto.setUpdatedAt(budget.getUpdatedAt());
        return dto;
    }

    @PutMapping("/households/{householdId}/budget")
    @ResponseStatus(HttpStatus.OK)
    public HouseholdBudgetGetDTO updateBudget(
            @RequestAttribute("authenticatedUserId") Long authenticatedUserId,
            @PathVariable Long householdId,
            @RequestBody HouseholdBudgetPutDTO budgetPutDTO) {

        HouseholdBudget budget = householdService.updateBudget(householdId, budgetPutDTO.getDailyCalorieTarget(), authenticatedUserId);
        HouseholdBudgetGetDTO dto = new HouseholdBudgetGetDTO();
        dto.setBudgetId(budget.getId());
        dto.setHouseholdId(budget.getHouseholdId());
        dto.setDailyCalorieTarget(budget.getDailyCalorieTarget());
        dto.setUpdatedAt(budget.getUpdatedAt());
        return dto;
    }

    @GetMapping("/households/{householdId}/stats")
    @ResponseStatus(HttpStatus.OK)
    public HouseholdStatsGetDTO getStats(
            @RequestAttribute("authenticatedUserId") Long authenticatedUserId,
            @PathVariable Long householdId,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        return householdService.getStats(householdId, startDate, endDate, authenticatedUserId);
    }

    @GetMapping("/households/{householdId}/consumption-logs")
    @ResponseStatus(HttpStatus.OK)
    public List<ConsumptionLogGetDTO> getConsumptionLogs(
            @RequestAttribute("authenticatedUserId") Long authenticatedUserId,
            @PathVariable Long householdId,
            @RequestParam(required = false) Integer limit) {

        return householdService.getConsumptionLogs(householdId, authenticatedUserId, limit);
    }
}
