package ch.uzh.ifi.hase.soprafs26.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ch.uzh.ifi.hase.soprafs26.entity.ConsumptionLog;

@Repository("consumptionLogRepository")
public interface ConsumptionLogRepository extends JpaRepository<ConsumptionLog, Long> {
    List<ConsumptionLog> findByPantryItemId(Long pantryItemId);
    void deleteByHouseholdId(Long householdId);
    List<ConsumptionLog> findByHouseholdIdAndConsumedAtBetween(Long householdId, Instant start, Instant end);
}