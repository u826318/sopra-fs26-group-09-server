package ch.uzh.ifi.hase.soprafs26.repository;

import java.time.Instant;
import java.util.List;
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
        PantryItem pantryItem = createPantryItem(1L, "7612345678901", "Greek Yogurt", 150.0, 2);

        entityManager.persistAndFlush(pantryItem);
        entityManager.clear();

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
        PantryItem pantryItem = createPantryItem(1L, "111", "Milk", 80.0, 1);

        entityManager.persistAndFlush(pantryItem);
        entityManager.clear();

        Optional<PantryItem> found = pantryItemRepository.findByIdAndHouseholdId(pantryItem.getId(), 1L);

        assertTrue(found.isPresent());
        assertEquals("Milk", found.get().getName());
        assertEquals(1L, found.get().getHouseholdId());
    }

    @Test
    void findByIdAndHouseholdId_wrongHousehold_returnsEmpty() {
        PantryItem pantryItem = createPantryItem(1L, "111", "Milk", 80.0, 1);

        entityManager.persistAndFlush(pantryItem);
        entityManager.clear();

        Optional<PantryItem> found = pantryItemRepository.findByIdAndHouseholdId(pantryItem.getId(), 2L);

        assertTrue(found.isEmpty());
    }

    @Test
    void findByHouseholdId_returnsOnlyItemsForThatHousehold() {
        PantryItem milk = createPantryItem(1L, "111", "Milk", 80.0, 1);
        PantryItem yogurt = createPantryItem(1L, "222", "Greek Yogurt", 150.0, 2);
        PantryItem pasta = createPantryItem(2L, "333", "Pasta", 350.0, 1);

        entityManager.persist(milk);
        entityManager.persist(yogurt);
        entityManager.persist(pasta);
        entityManager.flush();
        entityManager.clear();

        List<PantryItem> found = pantryItemRepository.findByHouseholdId(1L);

        assertEquals(2, found.size());
        assertTrue(found.stream().allMatch(item -> item.getHouseholdId().equals(1L)));
        assertTrue(found.stream().anyMatch(item -> item.getName().equals("Milk")));
        assertTrue(found.stream().anyMatch(item -> item.getName().equals("Greek Yogurt")));
    }

    @Test
    void findByHouseholdId_notFound_returnsEmptyList() {
        PantryItem milk = createPantryItem(1L, "111", "Milk", 80.0, 1);

        entityManager.persistAndFlush(milk);
        entityManager.clear();

        List<PantryItem> found = pantryItemRepository.findByHouseholdId(999L);

        assertTrue(found.isEmpty());
    }

    @Test
    void findByHouseholdIdAndBarcode_returnsOnlyMatchingItems() {
        PantryItem milkFirstPackage = createPantryItem(1L, "111", "Milk", 80.0, 1);
        PantryItem milkSecondPackage = createPantryItem(1L, "111", "Milk", 80.0, 3);
        PantryItem sameBarcodeOtherHousehold = createPantryItem(2L, "111", "Milk", 80.0, 1);
        PantryItem differentBarcodeSameHousehold = createPantryItem(1L, "222", "Greek Yogurt", 150.0, 2);

        entityManager.persist(milkFirstPackage);
        entityManager.persist(milkSecondPackage);
        entityManager.persist(sameBarcodeOtherHousehold);
        entityManager.persist(differentBarcodeSameHousehold);
        entityManager.flush();
        entityManager.clear();

        List<PantryItem> found = pantryItemRepository.findByHouseholdIdAndBarcode(1L, "111");

        assertEquals(2, found.size());
        assertTrue(found.stream().allMatch(item -> item.getHouseholdId().equals(1L)));
        assertTrue(found.stream().allMatch(item -> item.getBarcode().equals("111")));
    }

    @Test
    void findByHouseholdIdAndBarcode_noMatch_returnsEmptyList() {
        PantryItem milk = createPantryItem(1L, "111", "Milk", 80.0, 1);

        entityManager.persistAndFlush(milk);
        entityManager.clear();

        List<PantryItem> found = pantryItemRepository.findByHouseholdIdAndBarcode(1L, "999");

        assertTrue(found.isEmpty());
    }

    @Test
    void deleteByHouseholdId_deletesOnlyItemsForThatHousehold() {
        PantryItem milk = createPantryItem(1L, "111", "Milk", 80.0, 1);
        PantryItem yogurt = createPantryItem(1L, "222", "Greek Yogurt", 150.0, 2);
        PantryItem pasta = createPantryItem(2L, "333", "Pasta", 350.0, 1);

        entityManager.persist(milk);
        entityManager.persist(yogurt);
        entityManager.persist(pasta);
        entityManager.flush();
        entityManager.clear();

        pantryItemRepository.deleteByHouseholdId(1L);
        entityManager.flush();
        entityManager.clear();

        List<PantryItem> deletedHouseholdItems = pantryItemRepository.findByHouseholdId(1L);
        List<PantryItem> remainingHouseholdItems = pantryItemRepository.findByHouseholdId(2L);

        assertTrue(deletedHouseholdItems.isEmpty());
        assertEquals(1, remainingHouseholdItems.size());
        assertEquals("Pasta", remainingHouseholdItems.get(0).getName());
        assertEquals(2L, remainingHouseholdItems.get(0).getHouseholdId());
    }

    private PantryItem createPantryItem(Long householdId, String barcode, String name,
                                        Double kcalPerPackage, Integer count) {
        PantryItem pantryItem = new PantryItem();
        pantryItem.setHouseholdId(householdId);
        pantryItem.setBarcode(barcode);
        pantryItem.setName(name);
        pantryItem.setKcalPerPackage(kcalPerPackage);
        pantryItem.setCount(count);
        pantryItem.setAddedAt(Instant.now());
        return pantryItem;
    }
}
