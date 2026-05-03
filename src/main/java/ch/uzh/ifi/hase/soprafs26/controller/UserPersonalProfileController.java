package ch.uzh.ifi.hase.soprafs26.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ch.uzh.ifi.hase.soprafs26.entity.UserPersonalProfile;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserPersonalProfileGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserPersonalProfilePostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.mapper.DTOMapper;
import ch.uzh.ifi.hase.soprafs26.service.UserPersonalProfileService;

@RestController
public class UserPersonalProfileController {

    private final UserPersonalProfileService userPersonalProfileService;

    UserPersonalProfileController(UserPersonalProfileService userPersonalProfileService) {
        this.userPersonalProfileService = userPersonalProfileService;
    }

    @GetMapping("/users/{userId}/personal-profile")
    @ResponseStatus(HttpStatus.OK)
    public UserPersonalProfileGetDTO getPersonalProfile(
            @RequestAttribute("authenticatedUserId") Long authenticatedUserId,
            @PathVariable Long userId) {
        
        UserPersonalProfile profile = userPersonalProfileService.getPersonalProfile(userId, authenticatedUserId);

        return DTOMapper.INSTANCE.convertEntityToUserPersonalProfileGetDTO(profile);
    }

    @PutMapping("/users/{userId}/personal-profile")
    @ResponseStatus(HttpStatus.OK)
    public UserPersonalProfileGetDTO createOrUpdatePersonalProfile(
        @RequestAttribute("authenticatedUserId") Long authenticatedUserId,
        @PathVariable Long userId,
        @RequestBody UserPersonalProfilePostDTO userPersonalProfilePostDTO
    ) {
        UserPersonalProfile profile = userPersonalProfileService.createOrUpdatePersonalProfile(
            userId,
            authenticatedUserId,
            userPersonalProfilePostDTO.getBirthDate(),
            userPersonalProfilePostDTO.getLifeStageGroup()
        );

        return DTOMapper.INSTANCE.convertEntityToUserPersonalProfileGetDTO(profile);
    }
}
