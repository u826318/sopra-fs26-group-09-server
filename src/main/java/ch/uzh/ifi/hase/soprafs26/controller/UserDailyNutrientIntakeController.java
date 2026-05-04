package ch.uzh.ifi.hase.soprafs26.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.rest.dto.DailyNutrientIntakeGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.mapper.DTOMapper;
import ch.uzh.ifi.hase.soprafs26.service.DailyNutrientIntakeService;

@RestController
public class UserDailyNutrientIntakeController {

    private final DailyNutrientIntakeService dailyNutrientIntakeService;

    public UserDailyNutrientIntakeController(DailyNutrientIntakeService dailyNutrientIntakeService) {
        this.dailyNutrientIntakeService = dailyNutrientIntakeService;
    }

    @GetMapping("/users/{userId}/daily-nutrient-intake")
    @ResponseStatus(HttpStatus.OK)
    public DailyNutrientIntakeGetDTO getDailyNutrientIntake(
            @RequestAttribute("authenticatedUserId") Long authenticatedUserId,
            @PathVariable Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        if (!authenticatedUserId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied.");
        }

        return DTOMapper.INSTANCE.convertEntityToDailyNutrientIntakeGetDTO(
                dailyNutrientIntakeService.getDailyIntakeOrEmpty(userId, date)
        );
    }
}
