package ch.uzh.ifi.hase.soprafs26.repository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import ch.uzh.ifi.hase.soprafs26.entity.HouseholdBudget;

@DataJpaTest
class HouseholdBudgetRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private HouseholdBudgetRepository householdBudgetRepository;

    @Test
    void findByHouseholdId_success() {
        HouseholdBudget budget = new HouseholdBudget();
        budget.setHouseholdId(1L);
        budget.setDailyCalorieTarget(2000.0);

        entityManager.persist(budget);
        entityManager.flush();

        Optional<HouseholdBudget> found = householdBudgetRepository.findByHouseholdId(1L);

        assertTrue(found.isPresent());
        assertNotNull(found.get().getId());
        assertEquals(1L, found.get().getHouseholdId());
        assertEquals(2000.0, found.get().getDailyCalorieTarget());
        assertNotNull(found.get().getUpdatedAt());
    }

    @Test
    void findByHouseholdId_notFound() {
        Optional<HouseholdBudget> found = householdBudgetRepository.findByHouseholdId(999L);
        assertTrue(found.isEmpty());
    }
}
