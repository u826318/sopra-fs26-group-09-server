package ch.uzh.ifi.hase.soprafs26.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ch.uzh.ifi.hase.soprafs26.entity.PantryItem;

@Repository("pantryItemRepository")
public interface PantryItemRepository extends JpaRepository<PantryItem, Long> {

    Optional<PantryItem> findByIdAndHouseholdId(Long id, Long householdId);

    List<PantryItem> findByHouseholdId(Long householdId);
    
    List<PantryItem> findByHouseholdIdAndBarcode(Long householdId, String barcode);

    void deleteByHouseholdId(Long householdId);
}