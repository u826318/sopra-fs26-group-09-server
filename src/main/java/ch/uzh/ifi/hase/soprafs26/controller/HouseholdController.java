package ch.uzh.ifi.hase.soprafs26.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ch.uzh.ifi.hase.soprafs26.entity.Household;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.mapper.DTOMapper;
import ch.uzh.ifi.hase.soprafs26.service.HouseholdService;

@RestController
public class HouseholdController {

    private final HouseholdService householdService;

    HouseholdController(HouseholdService householdService) {
        this.householdService = householdService;
    }

    @PostMapping("/households")
    @ResponseStatus(HttpStatus.CREATED)
    public HouseholdGetDTO createHousehold(
            @RequestAttribute("authenticatedUserId") Long authenticatedUserId,
            @RequestBody HouseholdPostDTO householdPostDTO) {

        Household created = householdService.createHousehold(householdPostDTO.getName(), authenticatedUserId);
        return DTOMapper.INSTANCE.convertEntityToHouseholdGetDTO(created);
    }
}
