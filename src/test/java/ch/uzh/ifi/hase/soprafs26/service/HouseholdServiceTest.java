package ch.uzh.ifi.hase.soprafs26.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.entity.Household;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdMemberRepository;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdRepository;

class HouseholdServiceTest {

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private HouseholdMemberRepository householdMemberRepository;

    @InjectMocks
    private HouseholdService householdService;

    private User owner;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

        owner = new User();
        owner.setId(1L);
        owner.setUsername("testUser");

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
        Household result = householdService.createHousehold("Smith Family", owner);

        assertNotNull(result);
        assertEquals("Smith Family", result.getName());
        assertEquals(owner.getId(), result.getOwnerId());
        assertNotNull(result.getInviteCode());
        assertEquals(6, result.getInviteCode().length());
        verify(householdRepository).save(any());
        verify(householdMemberRepository).save(any());
    }

    @Test
    void createHousehold_nameTrimsWhitespace() {
        Household result = householdService.createHousehold("  My House  ", owner);

        assertEquals("My House", result.getName());
    }

    @Test
    void createHousehold_emptyName_throwsBadRequest() {
        assertThrows(ResponseStatusException.class,
                () -> householdService.createHousehold("", owner));
    }

    @Test
    void createHousehold_nullName_throwsBadRequest() {
        assertThrows(ResponseStatusException.class,
                () -> householdService.createHousehold(null, owner));
    }
}
