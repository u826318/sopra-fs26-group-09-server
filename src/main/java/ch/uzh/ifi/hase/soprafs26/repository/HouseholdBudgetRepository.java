package ch.uzh.ifi.hase.soprafs26.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ch.uzh.ifi.hase.soprafs26.entity.HouseholdBudget;

@Repository("householdBudgetRepository")
public interface HouseholdBudgetRepository extends JpaRepository<HouseholdBudget, Long> {
    Optional<HouseholdBudget> findByHouseholdId(Long householdId);
}
