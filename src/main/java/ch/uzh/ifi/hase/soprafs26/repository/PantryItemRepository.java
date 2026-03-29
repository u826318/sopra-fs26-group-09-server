package ch.uzh.ifi.hase.soprafs26.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ch.uzh.ifi.hase.soprafs26.entity.PantryItem;

@Repository("pantryItemRepository")
public interface PantryItemRepository extends JpaRepository<PantryItem, Long> {
}

    /**
     * Future: retrieve pantry items by household.
     * This will be used once Household entity is implemented.
     */
    // List<PantryItem> findByHouseholdId(Long householdId);

    /**
     * Future: find pantry items by barcode (optional feature).
     */
    // List<PantryItem> findByBarcode(String barcode);
