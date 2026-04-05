package ch.uzh.ifi.hase.soprafs26.repository;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import ch.uzh.ifi.hase.soprafs26.entity.PantryItem;

@DataJpaTest
class PantryItemRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PantryItemRepository pantryItemRepository;

    @Test
    void savePantryItem_success() {
        PantryItem pantryItem = new PantryItem();
        pantryItem.setHouseholdId(1L);
        pantryItem.setBarcode("7612345678901");
        pantryItem.setName("Greek Yogurt");
        pantryItem.setKcalPerPackage(150.0);
        pantryItem.setCount(2);
        pantryItem.setAddedAt(Instant.now());

        entityManager.persist(pantryItem);
        entityManager.flush();

        Optional<PantryItem> found = pantryItemRepository.findById(pantryItem.getId());

        assertTrue(found.isPresent());
        assertNotNull(found.get().getId());
        assertEquals(1L, found.get().getHouseholdId());
        assertEquals("7612345678901", found.get().getBarcode());
        assertEquals("Greek Yogurt", found.get().getName());
        assertEquals(150.0, found.get().getKcalPerPackage(), 0.001);
        assertEquals(2, found.get().getCount());
    }

    @Test
    void findByIdAndHouseholdId_success() {
        PantryItem pantryItem = new PantryItem();
        pantryItem.setHouseholdId(1L);
        pantryItem.setBarcode("111");
        pantryItem.setName("Milk");
        pantryItem.setKcalPerPackage(80.0);
        pantryItem.setCount(1);
        pantryItem.setAddedAt(Instant.now());

        entityManager.persist(pantryItem);
        entityManager.flush();

        Optional<PantryItem> found = pantryItemRepository.findByIdAndHouseholdId(pantryItem.getId(), 1L);

        assertTrue(found.isPresent());
        assertEquals("Milk", found.get().getName());
    }
}