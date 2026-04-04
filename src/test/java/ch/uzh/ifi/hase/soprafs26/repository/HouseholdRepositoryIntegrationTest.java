package ch.uzh.ifi.hase.soprafs26.repository;

import java.util.Optional;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import ch.uzh.ifi.hase.soprafs26.entity.Household;

@DataJpaTest
class HouseholdRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private HouseholdRepository householdRepository;

    @Test
    void saveHousehold_success() {
        Household household = new Household();
        household.setName("Smith Family");
        household.setInviteCode("ABC123");
        household.setInviteCodeExpiresAt(Instant.now().plusSeconds(60));
        household.setOwnerId(1L);

        entityManager.persist(household);
        entityManager.flush();

        Optional<Household> found = householdRepository.findById(household.getId());

        assertTrue(found.isPresent());
        assertNotNull(found.get().getId());
        assertEquals("Smith Family", found.get().getName());
        assertEquals("ABC123", found.get().getInviteCode());
        assertNotNull(found.get().getInviteCodeExpiresAt());
        assertEquals(1L, found.get().getOwnerId());
        assertNotNull(found.get().getCreatedAt());
    }

    @Test
    void findByInviteCode_success() {
        Household household = new Household();
        household.setName("Jones Family");
        household.setInviteCode("XYZ789");
        household.setInviteCodeExpiresAt(Instant.now().plusSeconds(60));
        household.setOwnerId(2L);

        entityManager.persist(household);
        entityManager.flush();

        Optional<Household> found = householdRepository.findByInviteCode("XYZ789");

        assertTrue(found.isPresent());
        assertEquals("Jones Family", found.get().getName());
    }

    @Test
    void findByInviteCode_notFound() {
        Optional<Household> found = householdRepository.findByInviteCode("DOESNOTEXIST");
        assertTrue(found.isEmpty());
    }
}
