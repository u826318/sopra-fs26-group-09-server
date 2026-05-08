package ch.uzh.ifi.hase.soprafs26.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.entity.LifeStageGroup;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.entity.UserPersonalProfile;
import ch.uzh.ifi.hase.soprafs26.repository.UserPersonalProfileRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;

class UserPersonalProfileServiceTest {

    private UserPersonalProfileRepository profileRepository;
    private UserRepository userRepository;
    private UserPersonalProfileService service;

    @BeforeEach
    void setUp() {
        profileRepository = mock(UserPersonalProfileRepository.class);
        userRepository = mock(UserRepository.class);
        service = new UserPersonalProfileService(profileRepository, userRepository);

        when(profileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void getPersonalProfile_returnsProfileForAuthenticatedUser() {
        UserPersonalProfile profile = profile(LocalDate.now().minusYears(25), LifeStageGroup.FEMALE);
        when(profileRepository.findByUser_Id(1L)).thenReturn(Optional.of(profile));

        UserPersonalProfile result = service.getPersonalProfile(1L, 1L);

        assertSame(profile, result);
    }

    @Test
    void getPersonalProfile_rejectsMissingOrDifferentAuthenticatedUser() {
        ResponseStatusException unauthorized = assertThrows(
                ResponseStatusException.class,
                () -> service.getPersonalProfile(1L, null)
        );
        ResponseStatusException forbidden = assertThrows(
                ResponseStatusException.class,
                () -> service.getPersonalProfile(1L, 2L)
        );

        assertEquals(401, unauthorized.getStatusCode().value());
        assertEquals(403, forbidden.getStatusCode().value());
    }

    @Test
    void getPersonalProfile_returnsNotFoundWhenProfileIsMissing() {
        when(profileRepository.findByUser_Id(1L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getPersonalProfile(1L, 1L)
        );

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void createOrUpdatePersonalProfile_createsProfileWhenMissing() {
        User user = user(1L);
        LocalDate birthDate = LocalDate.now().minusYears(30);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(profileRepository.findByUser_Id(1L)).thenReturn(Optional.empty());

        UserPersonalProfile result = service.createOrUpdatePersonalProfile(
                1L,
                1L,
                birthDate,
                LifeStageGroup.MALE
        );

        assertSame(user, result.getUser());
        assertEquals(birthDate, result.getBirthDate());
        assertEquals(LifeStageGroup.MALE, result.getLifeStageGroup());
        verify(profileRepository).save(result);
    }

    @Test
    void createOrUpdatePersonalProfile_updatesExistingProfile() {
        User user = user(1L);
        UserPersonalProfile existing = profile(LocalDate.now().minusYears(20), LifeStageGroup.FEMALE);
        LocalDate newBirthDate = LocalDate.now().minusYears(35);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(profileRepository.findByUser_Id(1L)).thenReturn(Optional.of(existing));

        UserPersonalProfile result = service.createOrUpdatePersonalProfile(
                1L,
                1L,
                newBirthDate,
                LifeStageGroup.BREASTFEEDING
        );

        assertSame(existing, result);
        assertSame(user, result.getUser());
        assertEquals(newBirthDate, result.getBirthDate());
        assertEquals(LifeStageGroup.BREASTFEEDING, result.getLifeStageGroup());
    }

    @Test
    void createOrUpdatePersonalProfile_returnsNotFoundWhenUserIsMissing() {
        LocalDate birthDate = LocalDate.now().minusYears(20);
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.createOrUpdatePersonalProfile(1L, 1L, birthDate, LifeStageGroup.FEMALE)
        );

        assertEquals(404, exception.getStatusCode().value());
        verify(profileRepository, never()).save(any());
    }

    @Test
    void createOrUpdatePersonalProfile_rejectsInvalidBirthDates() {
        assertBadRequestForBirthDate(null);
        assertBadRequestForBirthDate(LocalDate.now().plusDays(1));
        assertBadRequestForBirthDate(LocalDate.now().minusMonths(6));
    }

    @Test
    void calculateAgeInMonths_returnsWholeMonthDifference() {
        LocalDate birthDate = LocalDate.now().minusYears(2).minusMonths(3);

        int result = service.calculateAgeInMonths(birthDate);

        assertEquals((int) ChronoUnit.MONTHS.between(birthDate, LocalDate.now()), result);
    }

    @Test
    void calculateAgeInMonths_rejectsMissingBirthDate() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.calculateAgeInMonths(null)
        );

        assertEquals(400, exception.getStatusCode().value());
    }

    private void assertBadRequestForBirthDate(LocalDate birthDate) {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.createOrUpdatePersonalProfile(1L, 1L, birthDate, LifeStageGroup.FEMALE)
        );
        assertEquals(400, exception.getStatusCode().value());
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private UserPersonalProfile profile(LocalDate birthDate, LifeStageGroup lifeStageGroup) {
        UserPersonalProfile profile = new UserPersonalProfile();
        profile.setBirthDate(birthDate);
        profile.setLifeStageGroup(lifeStageGroup);
        return profile;
    }
}
