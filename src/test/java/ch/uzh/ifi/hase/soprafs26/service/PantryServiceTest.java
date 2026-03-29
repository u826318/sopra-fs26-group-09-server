package ch.uzh.ifi.hase.soprafs26.service;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.uzh.ifi.hase.soprafs26.entity.PantryItem;
import ch.uzh.ifi.hase.soprafs26.repository.PantryItemRepository;

class PantryServiceTest {

    @Test
    void calculateTotalCalories_success() {
        // mock repository
        PantryItemRepository mockRepo = mock(PantryItemRepository.class);

        PantryItem item1 = new PantryItem();
        item1.setKcalPerPackage(100.0);
        item1.setCount(2); // 200

        PantryItem item2 = new PantryItem();
        item2.setKcalPerPackage(250.0);
        item2.setCount(1); // 250

        when(mockRepo.findAll()).thenReturn(List.of(item1, item2));

        PantryService pantryService = new PantryService(mockRepo);

        double result = pantryService.calculateTotalCalories();

        assertEquals(450.0, result);
    }
}