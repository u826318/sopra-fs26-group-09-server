package ch.uzh.ifi.hase.soprafs26.service;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.PantryItemPostDTO;
import ch.uzh.ifi.hase.soprafs26.websocket.PantryUpdateMessage;

class PantryServiceTest {

    private PantryItemRepository mockPantryRepo;
    private ConsumptionLogRepository mockConsumptionRepo;
    private HouseholdRepository mockHouseholdRepo;
    private HouseholdMemberRepository mockHouseholdMemberRepo;
    private UserRepository mockUserRepo;
    private PantryBroadcastService mockBroadcastService;
    private PantryService pantryService;

    @BeforeEach
    void setUp() {
        mockPantryRepo = mock(PantryItemRepository.class);
        mockConsumptionRepo = mock(ConsumptionLogRepository.class);
        mockHouseholdRepo = mock(HouseholdRepository.class);
        mockHouseholdMemberRepo = mock(HouseholdMemberRepository.class);
        mockUserRepo = mock(UserRepository.class);
        mockBroadcastService = mock(PantryBroadcastService.class);

        pantryService = new PantryService(
                mockPantryRepo,
                mockConsumptionRepo,
                mockHouseholdRepo,
                mockHouseholdMemberRepo,
                mockUserRepo,
                mockBroadcastService
        );

        when(mockUserRepo.findById(anyLong())).thenReturn(Optional.empty());
        when(mockPantryRepo.findByHouseholdId(anyLong())).thenReturn(List.of());
    }

    @Test
    void calculateTotalCalories_success() {
        PantryItem item1 = new PantryItem();
        item1.setHouseholdId(1L);
        item1.setKcalPerPackage(100.0);
        item1.setCount(2);

        PantryItem item2 = new PantryItem();
        item2.setHouseholdId(1L);
        item2.setKcalPerPackage(250.0);
        item2.setCount(1);

        when(mockPantryRepo.findByHouseholdId(1L)).thenReturn(List.of(item1, item2));

        double result = pantryService.calculateTotalCalories(1L);

        assertEquals(450.0, result, 0.001);
    }

    @Test
    void getPantryItems_success() {
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

        List<PantryItem> result = pantryService.getPantryItems(1L, 99L);

        assertEquals(2, result.size());
        assertEquals("Milk", result.get(0).getName());
        assertEquals("Bread", result.get(1).getName());
    }

    @Test
    void getPantryItems_throwsException_whenUserIsNotMember() {
        Household household = new Household();
        household.setId(1L);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.getPantryItems(1L, 99L)
        );

        assertEquals("User is not a member of this household.", exception.getMessage());
    }

    @Test
    void addItem_success_savesPantryItem() {
        Household household = new Household();
        household.setId(1L);

        PantryItemPostDTO postDTO = new PantryItemPostDTO();
        postDTO.setBarcode(" 7613035974685 ");
        postDTO.setName(" Chocolate Bar ");
        postDTO.setKcalPerPackage(250.0);
        postDTO.setQuantity(3);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.save(any(PantryItem.class))).thenAnswer(invocation -> {
            PantryItem saved = invocation.getArgument(0);
            saved.setId(12L);
            return saved;
        });

        PantryItem result = pantryService.addItem(1L, postDTO, 99L);

        assertEquals(12L, result.getId());
        assertEquals(1L, result.getHouseholdId());
        assertEquals("7613035974685", result.getBarcode());
        assertEquals("Chocolate Bar", result.getName());
        assertEquals(250.0, result.getKcalPerPackage(), 0.001);
        assertEquals(3, result.getCount());
        assertTrue(result.getAddedAt() != null);

        verify(mockPantryRepo, times(1)).save(any(PantryItem.class));
        verify(mockBroadcastService, times(1)).broadcastPantryUpdate(any(Long.class), any(PantryUpdateMessage.class));
    }

    @Test
    void addItem_success_broadcastsEventType() {
        Household household = new Household();
        household.setId(1L);

        PantryItemPostDTO postDTO = new PantryItemPostDTO();
        postDTO.setBarcode("7613035974685");
        postDTO.setName("Chocolate Bar");
        postDTO.setKcalPerPackage(250.0);
        postDTO.setQuantity(1);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.save(any(PantryItem.class))).thenAnswer(inv -> {
            PantryItem saved = inv.getArgument(0);
            saved.setId(5L);
            return saved;
        });

        pantryService.addItem(1L, postDTO, 99L);

        verify(mockBroadcastService).broadcastPantryUpdate(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.argThat(msg -> "ITEM_ADDED".equals(msg.getEventType()))
        );
    }

    @Test
    void addItem_throwsException_whenQuantityIsInvalid() {
        PantryItemPostDTO postDTO = new PantryItemPostDTO();
        postDTO.setBarcode("7613035974685");
        postDTO.setName("Chocolate Bar");
        postDTO.setKcalPerPackage(250.0);
        postDTO.setQuantity(0);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.addItem(1L, postDTO, 99L)
        );

        assertEquals("Quantity must be greater than zero.", exception.getMessage());
        verify(mockPantryRepo, never()).save(any(PantryItem.class));
    }

    @Test
    void addItem_throwsException_whenHouseholdNotFound() {
        PantryItemPostDTO postDTO = new PantryItemPostDTO();
        postDTO.setBarcode("7613035974685");
        postDTO.setName("Chocolate Bar");
        postDTO.setKcalPerPackage(250.0);
        postDTO.setQuantity(3);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> pantryService.addItem(1L, postDTO, 99L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(mockPantryRepo, never()).save(any(PantryItem.class));
    }

    @Test
    void addItem_throwsException_whenUserIsNotMember() {
        Household household = new Household();
        household.setId(1L);

        PantryItemPostDTO postDTO = new PantryItemPostDTO();
        postDTO.setBarcode("7613035974685");
        postDTO.setName("Chocolate Bar");
        postDTO.setKcalPerPackage(250.0);
        postDTO.setQuantity(3);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.addItem(1L, postDTO, 99L)
        );

        assertEquals("User is not a member of this household.", exception.getMessage());
        verify(mockPantryRepo, never()).save(any(PantryItem.class));
    }

    @Test
    void consumeItem_success_updatesCountAndSavesLog() {
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

        PantryService.ConsumeResult result = pantryService.consumeItem(1L, 10L, 2, 99L);

        assertEquals(10L, result.getItemId());
        assertEquals(3, result.getRemainingCount());
        assertEquals(200.0, result.getConsumedCalories(), 0.001);
        assertFalse(result.isRemoved());

        verify(mockConsumptionRepo, times(1)).save(any(ConsumptionLog.class));
        verify(mockPantryRepo, times(1)).save(item);
        verify(mockPantryRepo, never()).delete(any(PantryItem.class));
        verify(mockBroadcastService, times(1)).broadcastPantryUpdate(any(Long.class), any(PantryUpdateMessage.class));
    }

    @Test
    void consumeItem_success_removesItemWhenCountReachesZero() {
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

        PantryService.ConsumeResult result = pantryService.consumeItem(1L, 10L, 2, 99L);

        assertEquals(10L, result.getItemId());
        assertEquals(0, result.getRemainingCount());
        assertEquals(240.0, result.getConsumedCalories(), 0.001);
        assertTrue(result.isRemoved());

        verify(mockConsumptionRepo, times(1)).save(any(ConsumptionLog.class));
        verify(mockPantryRepo, times(1)).delete(item);
        verify(mockBroadcastService, times(1)).broadcastPantryUpdate(any(Long.class), any(PantryUpdateMessage.class));
    }

    @Test
    void consumeItem_success_broadcastsEventType() {
        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);
        item.setKcalPerPackage(100.0);
        item.setCount(3);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.of(item));

        pantryService.consumeItem(1L, 10L, 1, 99L);

        verify(mockBroadcastService).broadcastPantryUpdate(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.argThat(msg -> "ITEM_CONSUMED".equals(msg.getEventType()))
        );
    }

    @Test
    void consumeItem_throwsException_whenQuantityExceedsAvailableCount() {
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

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.consumeItem(1L, 10L, 5, 99L)
        );

        assertEquals("Consumed quantity exceeds available quantity.", exception.getMessage());
        verify(mockConsumptionRepo, never()).save(any(ConsumptionLog.class));
    }

    @Test
    void consumeItem_throwsException_whenQuantityIsInvalid() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.consumeItem(1L, 10L, 0, 99L)
        );

        assertEquals("Quantity must be greater than zero.", exception.getMessage());
    }

    @Test
    void consumeItem_throwsException_whenHouseholdNotFound() {
        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> pantryService.consumeItem(1L, 10L, 1, 99L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void consumeItem_throwsException_whenUserIsNotMember() {
        Household household = new Household();
        household.setId(1L);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.consumeItem(1L, 10L, 1, 99L)
        );

        assertEquals("User is not a member of this household.", exception.getMessage());
    }

    @Test
    void consumeItem_throwsException_whenPantryItemNotFound() {
        Household household = new Household();
        household.setId(1L);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> pantryService.consumeItem(1L, 10L, 1, 99L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void addItem_success_mergesExistingItemWithSameBarcode() {
        Household household = new Household();
        household.setId(1L);

        PantryItem existing = new PantryItem();
        existing.setId(5L);
        existing.setHouseholdId(1L);
        existing.setBarcode("7613035974685");
        existing.setName("Old Name");
        existing.setKcalPerPackage(200.0);
        existing.setCount(3);
        existing.setAddedAt(java.time.Instant.now());

        PantryItemPostDTO postDTO = new PantryItemPostDTO();
        postDTO.setBarcode("7613035974685");
        postDTO.setName("Chocolate Bar");
        postDTO.setKcalPerPackage(250.0);
        postDTO.setQuantity(2);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByHouseholdIdAndBarcode(1L, "7613035974685")).thenReturn(List.of(existing));
        when(mockPantryRepo.save(any(PantryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        PantryItem result = pantryService.addItem(1L, postDTO, 99L);

        assertEquals(5L, result.getId());
        assertEquals(5, result.getCount());
        assertEquals("Chocolate Bar", result.getName());
        assertEquals(250.0, result.getKcalPerPackage(), 0.001);
        verify(mockPantryRepo, times(1)).save(existing);
    }

    @Test
    void removeItem_success_updatesCount() {
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

        PantryService.ConsumeResult result = pantryService.removeItem(1L, 10L, 2, 99L);

        assertEquals(10L, result.getItemId());
        assertEquals(3, result.getRemainingCount());
        assertEquals(0.0, result.getConsumedCalories(), 0.001);
        assertFalse(result.isRemoved());

        verify(mockPantryRepo, times(1)).save(item);
        verify(mockPantryRepo, never()).delete(any(PantryItem.class));
        verify(mockBroadcastService, times(1)).broadcastPantryUpdate(any(Long.class), any(PantryUpdateMessage.class));
    }

    @Test
    void removeItem_success_removesItemWhenCountReachesZero() {
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

        PantryService.ConsumeResult result = pantryService.removeItem(1L, 10L, 2, 99L);

        assertEquals(0, result.getRemainingCount());
        assertTrue(result.isRemoved());

        verify(mockPantryRepo, times(1)).delete(item);
        verify(mockBroadcastService, times(1)).broadcastPantryUpdate(any(Long.class), any(PantryUpdateMessage.class));
    }

    @Test
    void removeItem_broadcastsEventType() {
        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);
        item.setKcalPerPackage(100.0);
        item.setCount(3);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.of(item));

        pantryService.removeItem(1L, 10L, 1, 99L);

        verify(mockBroadcastService).broadcastPantryUpdate(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.argThat(msg -> "ITEM_REMOVED".equals(msg.getEventType()))
        );
    }

    @Test
    void removeItem_throwsException_whenQuantityExceedsAvailableCount() {
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

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.removeItem(1L, 10L, 5, 99L)
        );

        assertEquals("Removed quantity exceeds available quantity.", exception.getMessage());
        verify(mockPantryRepo, never()).delete(any(PantryItem.class));
    }

    @Test
    void removeItem_throwsException_whenQuantityIsInvalid() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.removeItem(1L, 10L, 0, 99L)
        );

        assertEquals("Quantity must be greater than zero.", exception.getMessage());
    }

    @Test
    void removeItem_throwsException_whenHouseholdNotFound() {
        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> pantryService.removeItem(1L, 10L, 1, 99L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void removeItem_throwsException_whenUserIsNotMember() {
        Household household = new Household();
        household.setId(1L);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.removeItem(1L, 10L, 1, 99L)
        );

        assertEquals("User is not a member of this household.", exception.getMessage());
    }

    @Test
    void removeItem_throwsException_whenPantryItemNotFound() {
        Household household = new Household();
        household.setId(1L);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> pantryService.removeItem(1L, 10L, 1, 99L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }
}
