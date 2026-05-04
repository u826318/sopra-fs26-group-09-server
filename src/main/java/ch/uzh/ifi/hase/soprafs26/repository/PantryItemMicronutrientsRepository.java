package ch.uzh.ifi.hase.soprafs26.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ch.uzh.ifi.hase.soprafs26.entity.PantryItemMicronutrients;

@Repository("pantryItemMicronutrientsRepository")
public interface PantryItemMicronutrientsRepository extends JpaRepository<PantryItemMicronutrients, Long> {

    Optional<PantryItemMicronutrients> findByPantryItemId(Long pantryItemId);
}
