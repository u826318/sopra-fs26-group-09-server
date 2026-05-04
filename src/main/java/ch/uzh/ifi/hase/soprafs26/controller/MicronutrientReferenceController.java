package ch.uzh.ifi.hase.soprafs26.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ch.uzh.ifi.hase.soprafs26.rest.dto.MicronutrientRequirementGetDTO;
import ch.uzh.ifi.hase.soprafs26.service.MicronutrientReferenceService;

@RestController
public class MicronutrientReferenceController {

    private final MicronutrientReferenceService micronutrientReferenceService;

    public MicronutrientReferenceController(MicronutrientReferenceService micronutrientReferenceService) {
        this.micronutrientReferenceService = micronutrientReferenceService;
    }

    @GetMapping("/users/{userId}/micronutrient-requirements")
    @ResponseStatus(HttpStatus.OK)
    public List<MicronutrientRequirementGetDTO> getMicronutrientRequirementsForUser(
        @RequestAttribute("authenticatedUserId") Long authenticatedUserId,
        @PathVariable Long userId
    ) {
        return micronutrientReferenceService.getRequirementsForUser(userId, authenticatedUserId);
    }
}
