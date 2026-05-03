package ch.uzh.ifi.hase.soprafs26.service;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.temporal.ChronoUnit;

import ch.uzh.ifi.hase.soprafs26.entity.LifeStageGroup;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.entity.UserPersonalProfile;
import ch.uzh.ifi.hase.soprafs26.repository.UserPersonalProfileRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;

@Service
@Transactional
public class UserPersonalProfileService {

    private final UserPersonalProfileRepository userPersonalProfileRepository;
    private final UserRepository userRepository;
    
    private final Logger log = LoggerFactory.getLogger(UserPersonalProfileService.class);

    public UserPersonalProfileService(
        UserPersonalProfileRepository userPersonalProfileRepository,
        UserRepository userRepository
        ) {
            this.userPersonalProfileRepository = userPersonalProfileRepository;
            this.userRepository = userRepository;
        }
    
    public UserPersonalProfile getPersonalProfile(Long userId, Long authenticatedUserId) {
        validateUserPersonalProfileAccess(userId, authenticatedUserId);

        UserPersonalProfile profile = userPersonalProfileRepository.findByUser_Id(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            "Personal profile was not found."));
        
        int ageMonths = calculateAgeInMonths(profile.getBirthDate());

        log.info("Calculated age in months for user {}: {}", userId, ageMonths);

        return profile;
        }

    public UserPersonalProfile createOrUpdatePersonalProfile(
        Long userId,
        Long authenticatedUserId,
        LocalDate birthDate,
        LifeStageGroup lifeStageGroup
    ) {
        validateUserPersonalProfileAccess(userId, authenticatedUserId);
        validateBirthDate(birthDate);

        User user = userRepository.findById(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User was not found."));

        UserPersonalProfile profile = this.userPersonalProfileRepository.findByUser_Id(userId).orElseGet(UserPersonalProfile::new);

        profile.setUser(user);
        profile.setBirthDate(birthDate);
        profile.setLifeStageGroup(lifeStageGroup);
        

        int ageMonths = calculateAgeInMonths(profile.getBirthDate());

        log.info("Calculated age in months for user {}: {}", userId, ageMonths);
        log.info("Life stage for user {}: {}", userId, lifeStageGroup);

        
        return userPersonalProfileRepository.save(profile);
    }

    private void validateUserPersonalProfileAccess(Long userId, Long authenticatedUserId) {
        if (authenticatedUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
            "Authentication is required");
        }
        if (!userId.equals(authenticatedUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "You can only access your own personal profile.");
        }
    }

    private void validateBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Birth date must be provided.");
        }

        LocalDate today = LocalDate.now();

        if (birthDate.isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Birth date cannot be in the future.");
        }

        LocalDate latestAllowedBirthDate = today.minusYears(1);

        if (birthDate.isAfter(latestAllowedBirthDate)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "User must be at least 1 year old."
            );
        }
    }
    
    public int calculateAgeInMonths(LocalDate birthDate) {
        if (birthDate == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Birth date must be provided."
            );
        }

        return (int) ChronoUnit.MONTHS.between(birthDate, LocalDate.now());
    }
}
