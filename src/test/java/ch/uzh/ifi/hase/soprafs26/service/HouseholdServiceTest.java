package ch.uzh.ifi.hase.soprafs26.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.entity.Household;
import ch.uzh.ifi.hase.soprafs26.entity.HouseholdMemberId;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdMemberRepository;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdRepository;

class HouseholdServiceTest {

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private HouseholdMemberRepository householdMemberRepository;

    @InjectMocks
    private HouseholdService householdService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

        when(householdRepository.save(any())).thenAnswer(inv -> {
            Household h = inv.getArgument(0);
            h.setId(10L);
            return h;
        });
        when(householdRepository.findByInviteCode(anyString())).thenReturn(Optional.empty());
        when(householdMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createHousehold_validInput_success() {
        Household result = householdService.createHousehold("Smith Family", 1L);

        assertNotNull(result);
        assertEquals("Smith Family", result.getName());
        assertEquals(1L, result.getOwnerId());
        assertNotNull(result.getInviteCode());
        assertEquals(6, result.getInviteCode().length());
        verify(householdRepository).save(any());
        verify(householdMemberRepository).save(any());
    }

    @Test
    void createHousehold_nameTrimsWhitespace() {
        Household result = householdService.createHousehold("  My House  ", 1L);

        assertEquals("My House", result.getName());
    }

    @Test
    void createHousehold_emptyName_throwsBadRequest() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> householdService.createHousehold("", 1L));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void createHousehold_nullName_throwsBadRequest() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> householdService.createHousehold(null, 1L));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void createHousehold_inviteCodeCollision_retriesAndSucceeds() {
        when(householdRepository.findByInviteCode(anyString()))
                .thenReturn(Optional.of(new Household()))
                .thenReturn(Optional.empty());

        Household result = householdService.createHousehold("Test House", 1L);

        assertNotNull(result);
        assertNotNull(result.getInviteCode());
    }

    @Test
    void createHousehold_inviteCodeMaxAttemptsExceeded_throws500() {
        when(householdRepository.findByInviteCode(anyString()))
                .thenReturn(Optional.of(new Household()));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> householdService.createHousehold("Test House", 1L));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());
    }

    @Test
    void regenerateInviteCode_owner_success() {
        Household existing = new Household();
        existing.setId(10L);
        existing.setOwnerId(1L);
        existing.setInviteCode("OLD111");

        when(householdRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(householdRepository.findByInviteCode(anyString()))
                .thenReturn(Optional.of(new Household()))
                .thenReturn(Optional.empty());

        Household result = householdService.regenerateInviteCode(10L, 1L);

        assertNotNull(result.getInviteCode());
        assertEquals(6, result.getInviteCode().length());
        verify(householdRepository).save(existing);
    }

    @Test
    void regenerateInviteCode_nonOwner_forbidden() {
        Household existing = new Household();
        existing.setId(10L);
        existing.setOwnerId(2L);

        when(householdRepository.findById(10L)).thenReturn(Optional.of(existing));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> householdService.regenerateInviteCode(10L, 1L));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void joinHouseholdByInviteCode_validCode_success() {
        Household household = new Household();
        household.setId(20L);
        household.setInviteCode("ABC123");

        when(householdRepository.findByInviteCode("ABC123")).thenReturn(Optional.of(household));
        when(householdMemberRepository.existsById(eq(new HouseholdMemberId(1L, 20L)))).thenReturn(false);

        Household result = householdService.joinHouseholdByInviteCode("abc123", 1L);

        assertEquals(20L, result.getId());
        verify(householdMemberRepository).save(any());
    }

    @Test
    void joinHouseholdByInviteCode_existingMember_conflict() {
        Household household = new Household();
        household.setId(20L);
        household.setInviteCode("ABC123");

        when(householdRepository.findByInviteCode("ABC123")).thenReturn(Optional.of(household));
        when(householdMemberRepository.existsById(eq(new HouseholdMemberId(1L, 20L)))).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> householdService.joinHouseholdByInviteCode("ABC123", 1L));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }
}
