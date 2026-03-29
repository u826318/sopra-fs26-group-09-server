package ch.uzh.ifi.hase.soprafs26.repository;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import ch.uzh.ifi.hase.soprafs26.entity.ConsumptionLog;

@DataJpaTest
class ConsumptionLogRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ConsumptionLogRepository consumptionLogRepository;

    @Test
    void findByPantryItemId_success() {
        ConsumptionLog log = new ConsumptionLog();
        log.setPantryItemId(1L);
        log.setConsumedQuantity(2);
        log.setConsumedCalories(300.0);
        log.setConsumedAt(Instant.now());

        entityManager.persist(log);
        entityManager.flush();

        List<ConsumptionLog> foundLogs = consumptionLogRepository.findByPantryItemId(1L);

        assertNotNull(foundLogs);
        assertEquals(1, foundLogs.size());
        assertEquals(1L, foundLogs.get(0).getPantryItemId());
        assertEquals(2, foundLogs.get(0).getConsumedQuantity());
        assertEquals(300.0, foundLogs.get(0).getConsumedCalories());
    }
}