package ch.uzh.ifi.hase.soprafs26.service;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.uzh.ifi.hase.soprafs26.entity.ConsumptionLog;
import ch.uzh.ifi.hase.soprafs26.entity.Household;
import ch.uzh.ifi.hase.soprafs26.entity.HouseholdMemberId;
import ch.uzh.ifi.hase.soprafs26.entity.PantryItem;
import ch.uzh.ifi.hase.soprafs26.repository.ConsumptionLogRepository;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdMemberRepository;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdRepository;
import ch.uzh.ifi.hase.soprafs26.repository.PantryItemRepository;

class PantryServiceTest {

    @Test
    void calculateTotalCalories_success() {
        PantryItemRepository mockPantryRepo = mock(PantryItemRepository.class);
        ConsumptionLogRepository mockConsumptionRepo = mock(ConsumptionLogRepository.class);
        HouseholdRepository mockHouseholdRepo = mock(HouseholdRepository.class);
        HouseholdMemberRepository mockHouseholdMemberRepo = mock(HouseholdMemberRepository.class);

        PantryItem item1 = new PantryItem();
        item1.setHouseholdId(1L);
        item1.setKcalPerPackage(100.0);
        item1.setCount(2);

        PantryItem item2 = new PantryItem();
        item2.setHouseholdId(1L);
        item2.setKcalPerPackage(250.0);
        item2.setCount(1);

        when(mockPantryRepo.findByHouseholdId(1L)).thenReturn(List.of(item1, item2));

        PantryService pantryService = new PantryService(
                mockPantryRepo,
                mockConsumptionRepo,
                mockHouseholdRepo,
                mockHouseholdMemberRepo
        );

        double result = pantryService.calculateTotalCalories(1L);

        assertEquals(450.0, result, 0.001);
    }

    @Test
    void getPantryItems_success() {
        PantryItemRepository mockPantryRepo = mock(PantryItemRepository.class);
        ConsumptionLogRepository mockConsumptionRepo = mock(ConsumptionLogRepository.class);
        HouseholdRepository mockHouseholdRepo = mock(HouseholdRepository.class);
        HouseholdMemberRepository mockHouseholdMemberRepo = mock(HouseholdMemberRepository.class);

        Household household = new Household();
        household.setId(1L);

        PantryItem item1 = new PantryItem();
        item1.setId(10L);
        item1.setHouseholdId(1L);
        item1.setName("Milk");

        PantryItem item2 = new PantryItem();
        item2.setId(11L);
        item2.setHouseholdId(1L);
        item2.setName("Bread");

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByHouseholdId(1L)).thenReturn(List.of(item1, item2));

        PantryService pantryService = new PantryService(
                mockPantryRepo,
                mockConsumptionRepo,
                mockHouseholdRepo,
                mockHouseholdMemberRepo
        );

        List<PantryItem> result = pantryService.getPantryItems(1L, 99L);

        assertEquals(2, result.size());
        assertEquals("Milk", result.get(0).getName());
        assertEquals("Bread", result.get(1).getName());
    }

    @Test
    void getPantryItems_throwsException_whenUserIsNotMember() {
        PantryItemRepository mockPantryRepo = mock(PantryItemRepository.class);
        ConsumptionLogRepository mockConsumptionRepo = mock(ConsumptionLogRepository.class);
        HouseholdRepository mockHouseholdRepo = mock(HouseholdRepository.class);
        HouseholdMemberRepository mockHouseholdMemberRepo = mock(HouseholdMemberRepository.class);

        Household household = new Household();
        household.setId(1L);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(false);

        PantryService pantryService = new PantryService(
                mockPantryRepo,
                mockConsumptionRepo,
                mockHouseholdRepo,
                mockHouseholdMemberRepo
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.getPantryItems(1L, 99L)
        );

        assertEquals("User is not a member of this household.", exception.getMessage());
    }

    @Test
    void consumeItem_success_updatesCountAndSavesLog() {
        PantryItemRepository mockPantryRepo = mock(PantryItemRepository.class);
        ConsumptionLogRepository mockConsumptionRepo = mock(ConsumptionLogRepository.class);
        HouseholdRepository mockHouseholdRepo = mock(HouseholdRepository.class);
        HouseholdMemberRepository mockHouseholdMemberRepo = mock(HouseholdMemberRepository.class);

        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);
        item.setKcalPerPackage(100.0);
        item.setCount(5);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.of(item));

        PantryService pantryService = new PantryService(
                mockPantryRepo,
                mockConsumptionRepo,
                mockHouseholdRepo,
                mockHouseholdMemberRepo
        );

        PantryService.ConsumeResult result = pantryService.consumeItem(1L, 10L, 2, 99L);

        assertEquals(10L, result.getItemId());
        assertEquals(3, result.getRemainingCount());
        assertEquals(200.0, result.getConsumedCalories(), 0.001);
        assertFalse(result.isRemoved());

        verify(mockConsumptionRepo, times(1)).save(any(ConsumptionLog.class));
        verify(mockPantryRepo, times(1)).save(item);
        verify(mockPantryRepo, never()).delete(any(PantryItem.class));
    }

    @Test
    void consumeItem_success_removesItemWhenCountReachesZero() {
        PantryItemRepository mockPantryRepo = mock(PantryItemRepository.class);
        ConsumptionLogRepository mockConsumptionRepo = mock(ConsumptionLogRepository.class);
        HouseholdRepository mockHouseholdRepo = mock(HouseholdRepository.class);
        HouseholdMemberRepository mockHouseholdMemberRepo = mock(HouseholdMemberRepository.class);

        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);
        item.setKcalPerPackage(120.0);
        item.setCount(2);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.of(item));

        PantryService pantryService = new PantryService(
                mockPantryRepo,
                mockConsumptionRepo,
                mockHouseholdRepo,
                mockHouseholdMemberRepo
        );

        PantryService.ConsumeResult result = pantryService.consumeItem(1L, 10L, 2, 99L);

        assertEquals(10L, result.getItemId());
        assertEquals(0, result.getRemainingCount());
        assertEquals(240.0, result.getConsumedCalories(), 0.001);
        assertTrue(result.isRemoved());

        verify(mockConsumptionRepo, times(1)).save(any(ConsumptionLog.class));
        verify(mockPantryRepo, times(1)).delete(item);
        verify(mockPantryRepo, never()).save(any(PantryItem.class));
    }

    @Test
    void consumeItem_throwsException_whenQuantityExceedsAvailableCount() {
        PantryItemRepository mockPantryRepo = mock(PantryItemRepository.class);
        ConsumptionLogRepository mockConsumptionRepo = mock(ConsumptionLogRepository.class);
        HouseholdRepository mockHouseholdRepo = mock(HouseholdRepository.class);
        HouseholdMemberRepository mockHouseholdMemberRepo = mock(HouseholdMemberRepository.class);

        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);
        item.setKcalPerPackage(100.0);
        item.setCount(2);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.of(item));

        PantryService pantryService = new PantryService(
                mockPantryRepo,
                mockConsumptionRepo,
                mockHouseholdRepo,
                mockHouseholdMemberRepo
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.consumeItem(1L, 10L, 5, 99L)
        );

        assertEquals("Consumed quantity exceeds available quantity.", exception.getMessage());
        verify(mockConsumptionRepo, never()).save(any(ConsumptionLog.class));
    }

    @Test
    void consumeItem_throwsException_whenQuantityIsInvalid() {
        PantryItemRepository mockPantryRepo = mock(PantryItemRepository.class);
        ConsumptionLogRepository mockConsumptionRepo = mock(ConsumptionLogRepository.class);
        HouseholdRepository mockHouseholdRepo = mock(HouseholdRepository.class);
        HouseholdMemberRepository mockHouseholdMemberRepo = mock(HouseholdMemberRepository.class);

        PantryService pantryService = new PantryService(
                mockPantryRepo,
                mockConsumptionRepo,
                mockHouseholdRepo,
                mockHouseholdMemberRepo
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.consumeItem(1L, 10L, 0, 99L)
        );

        assertEquals("Quantity must be greater than zero.", exception.getMessage());
    }

    @Test
    void consumeItem_throwsException_whenHouseholdNotFound() {
        PantryItemRepository mockPantryRepo = mock(PantryItemRepository.class);
        ConsumptionLogRepository mockConsumptionRepo = mock(ConsumptionLogRepository.class);
        HouseholdRepository mockHouseholdRepo = mock(HouseholdRepository.class);
        HouseholdMemberRepository mockHouseholdMemberRepo = mock(HouseholdMemberRepository.class);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.empty());

        PantryService pantryService = new PantryService(
                mockPantryRepo,
                mockConsumptionRepo,
                mockHouseholdRepo,
                mockHouseholdMemberRepo
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.consumeItem(1L, 10L, 1, 99L)
        );

        assertEquals("Household not found.", exception.getMessage());
    }

    @Test
    void consumeItem_throwsException_whenUserIsNotMember() {
        PantryItemRepository mockPantryRepo = mock(PantryItemRepository.class);
        ConsumptionLogRepository mockConsumptionRepo = mock(ConsumptionLogRepository.class);
        HouseholdRepository mockHouseholdRepo = mock(HouseholdRepository.class);
        HouseholdMemberRepository mockHouseholdMemberRepo = mock(HouseholdMemberRepository.class);

        Household household = new Household();
        household.setId(1L);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(false);

        PantryService pantryService = new PantryService(
                mockPantryRepo,
                mockConsumptionRepo,
                mockHouseholdRepo,
                mockHouseholdMemberRepo
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.consumeItem(1L, 10L, 1, 99L)
        );

        assertEquals("User is not a member of this household.", exception.getMessage());
    }

    @Test
    void consumeItem_throwsException_whenPantryItemNotFound() {
        PantryItemRepository mockPantryRepo = mock(PantryItemRepository.class);
        ConsumptionLogRepository mockConsumptionRepo = mock(ConsumptionLogRepository.class);
        HouseholdRepository mockHouseholdRepo = mock(HouseholdRepository.class);
        HouseholdMemberRepository mockHouseholdMemberRepo = mock(HouseholdMemberRepository.class);

        Household household = new Household();
        household.setId(1L);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.empty());

        PantryService pantryService = new PantryService(
                mockPantryRepo,
                mockConsumptionRepo,
                mockHouseholdRepo,
                mockHouseholdMemberRepo
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.consumeItem(1L, 10L, 1, 99L)
        );

        assertEquals("Pantry item not found in this household.", exception.getMessage());
    }
}