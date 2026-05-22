package ch.uzh.ifi.hase.soprafs26.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InOrder;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetLookupService;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetProductMapper;
import ch.uzh.ifi.hase.soprafs26.websocket.PantryUpdateMessage;

class PantryServiceTest {

    private PantryItemRepository mockPantryRepo;
    private ConsumptionLogRepository mockConsumptionRepo;
    private HouseholdRepository mockHouseholdRepo;
    private HouseholdMemberRepository mockHouseholdMemberRepo;
    private UserRepository mockUserRepo;
    private PantryBroadcastService mockBroadcastService;
    private PantryItemMicronutrientService mockMicronutrientService;
    private DailyNutrientIntakeService mockDailyNutrientIntakeService;
    private PantryService pantryService;
    private MealPortionEstimateService mockMealPortionEstimateService;
    private LocalDatasetLookupService mockLocalDatasetLookupService;
    private LocalDatasetProductMapper mockLocalDatasetProductMapper;

    @BeforeEach
    void setUp() {
        mockPantryRepo = mock(PantryItemRepository.class);
        mockConsumptionRepo = mock(ConsumptionLogRepository.class);
        mockHouseholdRepo = mock(HouseholdRepository.class);
        mockHouseholdMemberRepo = mock(HouseholdMemberRepository.class);
        mockUserRepo = mock(UserRepository.class);
        mockBroadcastService = mock(PantryBroadcastService.class);
        mockMicronutrientService = mock(PantryItemMicronutrientService.class);
        mockDailyNutrientIntakeService = mock(DailyNutrientIntakeService.class);
        mockMealPortionEstimateService = mock(MealPortionEstimateService.class);
        mockLocalDatasetLookupService = mock(LocalDatasetLookupService.class);
        mockLocalDatasetProductMapper = mock(LocalDatasetProductMapper.class);

        pantryService = new PantryService(
                mockPantryRepo,
                mockConsumptionRepo,
                mockHouseholdRepo,
                mockHouseholdMemberRepo,
                mockUserRepo,
                mockBroadcastService,
                mockMicronutrientService,
                mockDailyNutrientIntakeService,
                mockMealPortionEstimateService,
                mockLocalDatasetLookupService,
                mockLocalDatasetProductMapper
        );

        when(mockUserRepo.findById(anyLong())).thenReturn(Optional.empty());
        when(mockPantryRepo.findByHouseholdId(anyLong())).thenReturn(List.of());

        // Default stub: no local dataset match, so addItem tests use the DTO values directly
        when(mockLocalDatasetLookupService.findRawRowByBarcode(any())).thenReturn(Optional.empty());
    }

    // Issue #114 — calories now computed per unit (g/ml/package)
    @Test
    void calculateTotalCalories_success() {
        PantryItem item1 = new PantryItem();
        item1.setHouseholdId(1L);
        item1.setAmountUnit("package");
        item1.setKcalPerPackage(100.0);
        item1.setAmount(2.0);

        PantryItem item2 = new PantryItem();
        item2.setHouseholdId(1L);
        item2.setAmountUnit("package");
        item2.setKcalPerPackage(250.0);
        item2.setAmount(1.0);

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

    // Issue #114 — use amount/amountUnit instead of quantity/count
    @Test
    void addItem_success_savesPantryItem() {
        Household household = new Household();
        household.setId(1L);

        PantryItemPostDTO postDTO = new PantryItemPostDTO();
        postDTO.setBarcode(" 7613035974685 ");
        postDTO.setName(" Chocolate Bar ");
        postDTO.setKcalPerPackage(250.0);
        postDTO.setAmount(3.0);
        postDTO.setAmountUnit("package");

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
        assertEquals(3.0, result.getAmount(), 0.001);
        assertEquals("package", result.getAmountUnit());
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
        postDTO.setAmount(1.0);
        postDTO.setAmountUnit("package");

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

    // Issue #114 — validation now checks amount > 0 instead of quantity
    @Test
    void addItem_throwsException_whenAmountIsInvalid() {
        PantryItemPostDTO postDTO = new PantryItemPostDTO();
        postDTO.setBarcode("7613035974685");
        postDTO.setName("Chocolate Bar");
        postDTO.setKcalPerPackage(250.0);
        postDTO.setAmount(0.0);
        postDTO.setAmountUnit("package");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.addItem(1L, postDTO, 99L)
        );

        assertEquals("Amount must be greater than zero.", exception.getMessage());
        verify(mockPantryRepo, never()).save(any(PantryItem.class));
    }

    @Test
    void addItem_throwsException_whenHouseholdNotFound() {
        PantryItemPostDTO postDTO = new PantryItemPostDTO();
        postDTO.setBarcode("7613035974685");
        postDTO.setName("Chocolate Bar");
        postDTO.setKcalPerPackage(250.0);
        postDTO.setAmount(3.0);
        postDTO.setAmountUnit("package");

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
        postDTO.setAmount(3.0);
        postDTO.setAmountUnit("package");

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.addItem(1L, postDTO, 99L)
        );

        assertEquals("User is not a member of this household.", exception.getMessage());
        verify(mockPantryRepo, never()).save(any(PantryItem.class));
    }

    // Issue #114 — consumeItem now works with amount (Double)
    @Test
    void consumeItem_success_updatesAmountAndSavesLog() {
        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);
        item.setAmountUnit("package");
        item.setKcalPerPackage(100.0);
        item.setAmount(5.0);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.of(item));

        // Issue #133 — consumeItem now takes Double amount
        PantryService.ConsumeResult result = pantryService.consumeItem(1L, 10L, 2.0, 99L);

        assertEquals(10L, result.getItemId());
        assertEquals(3.0, result.getRemainingAmount(), 0.001);
        assertEquals(200.0, result.getConsumedCalories(), 0.001);
        assertFalse(result.isRemoved());

        verify(mockConsumptionRepo, times(1)).save(any(ConsumptionLog.class));
        verify(mockPantryRepo, times(1)).save(item);
        verify(mockPantryRepo, never()).delete(any(PantryItem.class));
        verify(mockBroadcastService, times(1)).broadcastPantryUpdate(any(Long.class), any(PantryUpdateMessage.class));
    }

    @Test
    void consumeItem_success_removesItemWhenAmountReachesZero() {
        // arrange
        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);
        item.setName("Organic Rice");
        item.setAmountUnit("package");
        item.setKcalPerPackage(120.0);
        item.setAmount(2.0);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.of(item));

        // act
        PantryService.ConsumeResult result = pantryService.consumeItem(1L, 10L, 2.0, 99L);

        // assert: returned result (state-based)
        assertEquals(10L, result.getItemId());
        assertEquals(0.0, result.getRemainingAmount(), 0.001);
        assertEquals(240.0, result.getConsumedCalories(), 0.001);
        assertTrue(result.isRemoved());

        // assert: persisted ConsumptionLog carries the correct payload
        ArgumentCaptor<ConsumptionLog> logCaptor = ArgumentCaptor.forClass(ConsumptionLog.class);
        verify(mockConsumptionRepo, times(1)).save(logCaptor.capture());
        ConsumptionLog savedLog = logCaptor.getValue();
        assertEquals(1L, savedLog.getHouseholdId());
        assertEquals(99L, savedLog.getUserId());
        assertEquals(10L, savedLog.getPantryItemId());
        assertEquals("Organic Rice", savedLog.getProductNameSnapshot());
        // Issue #133 — consumedQuantity is rounded from Double amount (2.0 → 2)
        assertEquals(2, savedLog.getConsumedQuantity());
        assertEquals(240.0, savedLog.getConsumedCalories(), 0.001);
        assertNotNull(savedLog.getConsumedAt());

        // assert: delete-branch took, save-branch did NOT (positive + negative)
        verify(mockPantryRepo, times(1)).delete(item);
        verify(mockPantryRepo, never()).save(item);

        // assert: broadcast message carries the correct event semantics
        ArgumentCaptor<PantryUpdateMessage> msgCaptor = ArgumentCaptor.forClass(PantryUpdateMessage.class);
        verify(mockBroadcastService, times(1)).broadcastPantryUpdate(eq(1L), msgCaptor.capture());
        PantryUpdateMessage msg = msgCaptor.getValue();
        assertEquals("ITEM_CONSUMED", msg.getEventType());
        assertEquals(1L, msg.getHouseholdId());
        assertEquals(99L, msg.getTriggeredByUserId());

        // assert: side effects happen in the correct order
        // (log must be persisted BEFORE the pantry row is deleted, and broadcast happens last)
        InOrder inOrder = inOrder(mockConsumptionRepo, mockPantryRepo, mockBroadcastService);
        inOrder.verify(mockConsumptionRepo).save(any(ConsumptionLog.class));
        inOrder.verify(mockPantryRepo).delete(item);
        inOrder.verify(mockBroadcastService).broadcastPantryUpdate(eq(1L), any(PantryUpdateMessage.class));
    }

    @Test
    void consumeItem_success_broadcastsEventType() {
        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);
        item.setAmountUnit("package");
        item.setKcalPerPackage(100.0);
        item.setAmount(3.0);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.of(item));

        pantryService.consumeItem(1L, 10L, 1.0, 99L);

        verify(mockBroadcastService).broadcastPantryUpdate(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.argThat(msg -> "ITEM_CONSUMED".equals(msg.getEventType()))
        );
    }

    @Test
    void consumeItem_withKcalOverride_updatesPantryItemAndPersistsOverriddenCalories() {
        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);
        item.setAmountUnit("package");
        item.setKcalPerPackage(100.0);
        item.setAmount(4.0);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.of(item));

        PantryService.ConsumeResult result = pantryService.consumeItem(1L, 10L, 2.0, 180.0, false, 99L);

        assertEquals(2.0, result.getRemainingAmount(), 0.001);
        assertEquals(360.0, result.getConsumedCalories(), 0.001);
        assertEquals(180.0, item.getKcalPerPackage(), 0.001);
        verify(mockPantryRepo, times(2)).save(item);

        ArgumentCaptor<ConsumptionLog> logCaptor = ArgumentCaptor.forClass(ConsumptionLog.class);
        verify(mockConsumptionRepo).save(logCaptor.capture());
        assertEquals(360.0, logCaptor.getValue().getConsumedCalories(), 0.001);
    }

    @Test
    void consumeItem_withSkipCalorieLogging_recordsUnknownCaloriesAndDoesNotOverwritePantryItem() {
        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);
        item.setAmountUnit("package");
        item.setKcalPerPackage(null);
        item.setAmount(3.0);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.of(item));

        PantryService.ConsumeResult result = pantryService.consumeItem(1L, 10L, 1.0, 250.0, true, 99L);

        assertEquals(2.0, result.getRemainingAmount(), 0.001);
        assertNull(result.getConsumedCalories());
        assertNull(item.getKcalPerPackage());

        ArgumentCaptor<ConsumptionLog> logCaptor = ArgumentCaptor.forClass(ConsumptionLog.class);
        verify(mockConsumptionRepo).save(logCaptor.capture());
        assertNull(logCaptor.getValue().getConsumedCalories());
        verify(mockPantryRepo, times(1)).save(item);
    }

    @Test
    void consumeItem_throwsException_whenKcalOverrideIsNegative() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.consumeItem(1L, 10L, 1.0, -1.0, false, 99L)
        );

        assertEquals("Calories per package must not be negative.", exception.getMessage());
    }

    @Test
    void consumeItem_throwsException_whenQuantityExceedsAvailableAmount() {
        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);
        item.setAmountUnit("package");
        item.setKcalPerPackage(100.0);
        item.setAmount(2.0);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.of(item));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.consumeItem(1L, 10L, 5.0, 99L)
        );

        assertEquals("Consumed quantity exceeds available quantity.", exception.getMessage());
        verify(mockConsumptionRepo, never()).save(any(ConsumptionLog.class));
    }

    @Test
    void consumeItem_throwsException_whenQuantityIsInvalid() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.consumeItem(1L, 10L, 0.0, 99L)
        );

        assertEquals("Quantity must be greater than zero.", exception.getMessage());
    }

    @Test
    void consumeItem_throwsException_whenHouseholdNotFound() {
        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> pantryService.consumeItem(1L, 10L, 1.0, 99L)
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
                () -> pantryService.consumeItem(1L, 10L, 1.0, 99L)
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
                () -> pantryService.consumeItem(1L, 10L, 1.0, 99L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    // Issue #133 — consumedUnit from amountUnit must be persisted in the consumption log
    @Test
    void consumeItem_savesConsumedUnitFromAmountUnitInLog() {
        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);
        item.setAmountUnit("g");
        item.setKcalPer100g(364.0);
        item.setAmount(500.0);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.of(item));

        pantryService.consumeItem(1L, 10L, 200.0, 99L);

        ArgumentCaptor<ConsumptionLog> captor = ArgumentCaptor.forClass(ConsumptionLog.class);
        verify(mockConsumptionRepo).save(captor.capture());
        assertEquals("g", captor.getValue().getConsumedUnit());
    }

    // Issue #158 — portion consumption with grams uses kcalPer100g and updates amount
    @Test
    void consumeItem_withGramUnit_convertsPortionToCaloriesAndUpdatesAmount() {
        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);
        item.setAmountUnit("g");
        item.setKcalPer100g(364.0);
        item.setAmount(500.0);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.of(item));

        PantryService.ConsumeResult result = pantryService.consumeItem(1L, 10L, 200.0, 99L);

        assertEquals(300.0, result.getRemainingAmount(), 0.001);
        assertEquals(728.0, result.getConsumedCalories(), 0.001);
        assertFalse(result.isRemoved());

        ArgumentCaptor<ConsumptionLog> logCaptor = ArgumentCaptor.forClass(ConsumptionLog.class);
        verify(mockConsumptionRepo).save(logCaptor.capture());

        ConsumptionLog savedLog = logCaptor.getValue();
        assertEquals(200, savedLog.getConsumedQuantity());
        assertEquals("g", savedLog.getConsumedUnit());
        assertEquals(728.0, savedLog.getConsumedCalories(), 0.001);

        verify(mockPantryRepo).save(item);
        verify(mockBroadcastService).broadcastPantryUpdate(eq(1L), any(PantryUpdateMessage.class));
    }

    // Issue #158 — portion consumption with milliliters uses kcalPer100ml
    @Test
    void consumeItem_withMilliliterUnit_convertsPortionToCaloriesAndUpdatesAmount() {
        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(11L);
        item.setHouseholdId(1L);
        item.setAmountUnit("ml");
        item.setKcalPer100ml(50.0);
        item.setAmount(750.0);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(11L, 1L)).thenReturn(Optional.of(item));

        PantryService.ConsumeResult result = pantryService.consumeItem(1L, 11L, 250.0, 99L);

        assertEquals(500.0, result.getRemainingAmount(), 0.001);
        assertEquals(125.0, result.getConsumedCalories(), 0.001);
        assertFalse(result.isRemoved());

        ArgumentCaptor<ConsumptionLog> logCaptor = ArgumentCaptor.forClass(ConsumptionLog.class);
        verify(mockConsumptionRepo).save(logCaptor.capture());

        ConsumptionLog savedLog = logCaptor.getValue();
        assertEquals(250, savedLog.getConsumedQuantity());
        assertEquals("ml", savedLog.getConsumedUnit());
        assertEquals(125.0, savedLog.getConsumedCalories(), 0.001);

        verify(mockPantryRepo).save(item);
        verify(mockBroadcastService).broadcastPantryUpdate(eq(1L), any(PantryUpdateMessage.class));
    }

    // Issue #158 — unknown calories still records consumption and updates quantity
    @Test
    void consumeItem_withoutNutritionInfo_recordsConsumptionWithNullCalories() {
        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(12L);
        item.setHouseholdId(1L);
        item.setAmountUnit("g");
        item.setKcalPer100g(null);
        item.setAmount(300.0);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(12L, 1L)).thenReturn(Optional.of(item));

        PantryService.ConsumeResult result = pantryService.consumeItem(1L, 12L, 100.0, 99L);

        assertEquals(200.0, result.getRemainingAmount(), 0.001);
        assertNull(result.getConsumedCalories());
        assertFalse(result.isRemoved());

        ArgumentCaptor<ConsumptionLog> logCaptor = ArgumentCaptor.forClass(ConsumptionLog.class);
        verify(mockConsumptionRepo).save(logCaptor.capture());

        assertEquals(100, logCaptor.getValue().getConsumedQuantity());
        assertEquals("g", logCaptor.getValue().getConsumedUnit());
        assertNull(logCaptor.getValue().getConsumedCalories());

        verify(mockPantryRepo).save(item);
    }

    // Issue #114 — mergeOrCreatePantryItem now merges on barcode+amountUnit match
    @Test
    void addItem_success_mergesExistingItemWithSameBarcodeAndUnit() {
        Household household = new Household();
        household.setId(1L);

        PantryItem existing = new PantryItem();
        existing.setId(5L);
        existing.setHouseholdId(1L);
        existing.setBarcode("7613035974685");
        existing.setName("Old Name");
        existing.setAmountUnit("package");
        existing.setKcalPerPackage(200.0);
        existing.setAmount(3.0);
        existing.setAddedAt(java.time.Instant.now());

        PantryItemPostDTO postDTO = new PantryItemPostDTO();
        postDTO.setBarcode("7613035974685");
        postDTO.setName("Chocolate Bar");
        postDTO.setKcalPerPackage(250.0);
        postDTO.setAmount(2.0);
        postDTO.setAmountUnit("package");

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByHouseholdIdAndBarcode(1L, "7613035974685")).thenReturn(List.of(existing));
        when(mockPantryRepo.save(any(PantryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        PantryItem result = pantryService.addItem(1L, postDTO, 99L);

        assertEquals(5L, result.getId());
        assertEquals(5.0, result.getAmount(), 0.001);
        assertEquals("Chocolate Bar", result.getName());
        assertEquals(250.0, result.getKcalPerPackage(), 0.001);
        verify(mockPantryRepo, times(1)).save(existing);
    }

    @Test
    void addItem_success_noBarcode_createsNewRowWithNullBarcode() {
        Household household = new Household();
        household.setId(1L);

        PantryItemPostDTO postDTO = new PantryItemPostDTO();
        postDTO.setBarcode(null);
        postDTO.setName("Homemade Jam");
        postDTO.setKcalPerPackage(180.0);
        postDTO.setAmount(1.0);
        postDTO.setAmountUnit("package");

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.save(any(PantryItem.class))).thenAnswer(inv -> {
            PantryItem saved = inv.getArgument(0);
            saved.setId(20L);
            return saved;
        });

        PantryItem result = pantryService.addItem(1L, postDTO, 99L);

        assertNull(result.getBarcode());
        assertEquals("Homemade Jam", result.getName());
        assertEquals(1.0, result.getAmount(), 0.001);
        verify(mockPantryRepo, times(1)).save(any(PantryItem.class));
        verify(mockPantryRepo, never()).findByHouseholdIdAndBarcode(anyLong(), any());
    }

    @Test
    void addItem_success_emptyBarcode_createsNewRowWithNullBarcode() {
        Household household = new Household();
        household.setId(1L);

        PantryItemPostDTO postDTO = new PantryItemPostDTO();
        postDTO.setBarcode("   ");
        postDTO.setName("Homemade Bread");
        postDTO.setKcalPerPackage(200.0);
        postDTO.setAmount(2.0);
        postDTO.setAmountUnit("package");

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.save(any(PantryItem.class))).thenAnswer(inv -> {
            PantryItem saved = inv.getArgument(0);
            saved.setId(21L);
            return saved;
        });

        PantryItem result = pantryService.addItem(1L, postDTO, 99L);

        assertNull(result.getBarcode());
        assertEquals("Homemade Bread", result.getName());
        verify(mockPantryRepo, times(1)).save(any(PantryItem.class));
        verify(mockPantryRepo, never()).findByHouseholdIdAndBarcode(anyLong(), any());
    }

    @Test
    void bulkAddItems_success_savesEachRowAndBroadcastsPerRow() {
        Household household = new Household();
        household.setId(1L);

        PantryItemPostDTO first = new PantryItemPostDTO();
        first.setBarcode("111");
        first.setName("A");
        first.setKcalPerPackage(100.0);
        first.setAmount(1.0);
        first.setAmountUnit("package");

        PantryItemPostDTO second = new PantryItemPostDTO();
        second.setBarcode("222");
        second.setName("B");
        second.setKcalPerPackage(200.0);
        second.setAmount(2.0);
        second.setAmountUnit("package");

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByHouseholdIdAndBarcode(eq(1L), any())).thenReturn(List.of());
        AtomicLong nextId = new AtomicLong(10L);
        when(mockPantryRepo.save(any(PantryItem.class))).thenAnswer(inv -> {
            PantryItem p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(nextId.getAndIncrement());
            }
            return p;
        });

        List<PantryItem> results = pantryService.bulkAddItems(1L, List.of(first, second), 99L);

        assertEquals(2, results.size());
        assertEquals("111", results.get(0).getBarcode());
        assertEquals("222", results.get(1).getBarcode());
        verify(mockPantryRepo, times(2)).save(any(PantryItem.class));
        verify(mockBroadcastService, times(2)).broadcastPantryUpdate(eq(1L), any(PantryUpdateMessage.class));
    }

    @Test
    void bulkAddItems_throwsWhenListEmpty() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.bulkAddItems(1L, List.of(), 99L));

        assertEquals("Bulk add payload must contain at least one item.", ex.getMessage());
        verify(mockHouseholdRepo, never()).findById(anyLong());
        verify(mockPantryRepo, never()).save(any(PantryItem.class));
    }

    @Test
    void bulkAddItems_throwsWhenListNull() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.bulkAddItems(1L, null, 99L));

        assertEquals("Bulk add payload must contain at least one item.", ex.getMessage());
        verify(mockHouseholdRepo, never()).findById(anyLong());
    }

    @Test
    void bulkAddItems_throwsWhenTooManyItems() {
        List<PantryItemPostDTO> oversized = new ArrayList<>(PantryService.MAX_ITEMS_PER_BULK_REQUEST + 1);
        for (int i = 0; i < PantryService.MAX_ITEMS_PER_BULK_REQUEST + 1; i++) {
            PantryItemPostDTO dto = new PantryItemPostDTO();
            dto.setBarcode("b" + i);
            dto.setName("n");
            dto.setKcalPerPackage(1.0);
            dto.setAmount(1.0);
            dto.setAmountUnit("package");
            oversized.add(dto);
        }

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.bulkAddItems(1L, oversized, 99L));

        assertEquals("Cannot add more than " + PantryService.MAX_ITEMS_PER_BULK_REQUEST
                + " items in one request.", ex.getMessage());
        verify(mockHouseholdRepo, never()).findById(anyLong());
    }

    @Test
    void bulkAddItems_validatesAllRowsBeforeHouseholdLookup() {
        PantryItemPostDTO bad = new PantryItemPostDTO();
        bad.setBarcode("1");
        bad.setName("x");
        bad.setKcalPerPackage(1.0);
        bad.setAmount(0.0);
        bad.setAmountUnit("package");

        assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.bulkAddItems(1L, List.of(bad), 99L));

        verify(mockHouseholdRepo, never()).findById(anyLong());
    }

    @Test
    void bulkAddItems_throwsWhenNotMember() {
        Household household = new Household();
        household.setId(1L);

        PantryItemPostDTO dto = new PantryItemPostDTO();
        dto.setBarcode("1");
        dto.setName("A");
        dto.setKcalPerPackage(10.0);
        dto.setAmount(1.0);
        dto.setAmountUnit("package");

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(false);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.bulkAddItems(1L, List.of(dto), 99L));

        assertEquals("User is not a member of this household.", ex.getMessage());
        verify(mockPantryRepo, never()).save(any(PantryItem.class));
    }

    // Issue #114 — removeItem now works with amount (Double)
    @Test
    void removeItem_success_updatesAmount() {
        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);
        item.setAmountUnit("package");
        item.setKcalPerPackage(100.0);
        item.setAmount(5.0);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.of(item));

        // Issue #133 — removeItem now takes Double amount
        PantryService.ConsumeResult result = pantryService.removeItem(1L, 10L, 2.0, 99L);

        assertEquals(10L, result.getItemId());
        assertEquals(3.0, result.getRemainingAmount(), 0.001);
        assertEquals(0.0, result.getConsumedCalories(), 0.001);
        assertFalse(result.isRemoved());

        verify(mockPantryRepo, times(1)).save(item);
        verify(mockPantryRepo, never()).delete(any(PantryItem.class));
        verify(mockBroadcastService, times(1)).broadcastPantryUpdate(any(Long.class), any(PantryUpdateMessage.class));
    }

    @Test
    void removeItem_success_removesItemWhenAmountReachesZero() {
        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);
        item.setAmountUnit("package");
        item.setKcalPerPackage(100.0);
        item.setAmount(2.0);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.of(item));

        PantryService.ConsumeResult result = pantryService.removeItem(1L, 10L, 2.0, 99L);

        assertEquals(0.0, result.getRemainingAmount(), 0.001);
        assertTrue(result.isRemoved());

        verify(mockPantryRepo, times(1)).delete(item);
        verify(mockBroadcastService, times(1)).broadcastPantryUpdate(any(Long.class), any(PantryUpdateMessage.class));
    }

    @Test
    void removeItem_broadcastsItemUpdated_whenAmountDecremented() {
        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);
        item.setAmountUnit("package");
        item.setKcalPerPackage(100.0);
        item.setAmount(3.0);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.of(item));

        pantryService.removeItem(1L, 10L, 1.0, 99L);

        verify(mockBroadcastService).broadcastPantryUpdate(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.argThat(msg -> "ITEM_UPDATED".equals(msg.getEventType()))
        );
    }

    @Test
    void removeItem_broadcastsItemRemoved_whenAmountReachesZero() {
        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);
        item.setAmountUnit("package");
        item.setKcalPerPackage(100.0);
        item.setAmount(2.0);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.of(item));

        pantryService.removeItem(1L, 10L, 2.0, 99L);

        verify(mockBroadcastService).broadcastPantryUpdate(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.argThat(msg -> "ITEM_REMOVED".equals(msg.getEventType()))
        );
    }

    @Test
    void removeItem_throwsException_whenQuantityExceedsAvailableAmount() {
        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);
        item.setAmountUnit("package");
        item.setKcalPerPackage(100.0);
        item.setAmount(2.0);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.of(item));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.removeItem(1L, 10L, 5.0, 99L)
        );

        assertEquals("Removed quantity exceeds available quantity.", exception.getMessage());
        verify(mockPantryRepo, never()).delete(any(PantryItem.class));
    }

    @Test
    void removeItem_throwsException_whenQuantityIsInvalid() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.removeItem(1L, 10L, 0.0, 99L)
        );

        assertEquals("Quantity must be greater than zero.", exception.getMessage());
    }

    @Test
    void removeItem_throwsException_whenHouseholdNotFound() {
        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> pantryService.removeItem(1L, 10L, 1.0, 99L)
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
                () -> pantryService.removeItem(1L, 10L, 1.0, 99L)
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
                () -> pantryService.removeItem(1L, 10L, 1.0, 99L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void getPantryItems_joinedMemberSeesSameSharedItems() {
        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);
        item.setBarcode("7613035974685");
        item.setName("Chocolate Bar");
        item.setAmountUnit("package");
        item.setKcalPerPackage(250.0);
        item.setAmount(2.0);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(new HouseholdMemberId(1L, 1L))).thenReturn(true);
        when(mockHouseholdMemberRepo.existsById(new HouseholdMemberId(2L, 1L))).thenReturn(true);
        when(mockPantryRepo.findByHouseholdId(1L)).thenReturn(List.of(item));

        List<PantryItem> ownerView = pantryService.getPantryItems(1L, 1L);
        List<PantryItem> joinedMemberView = pantryService.getPantryItems(1L, 2L);

        assertEquals(1, ownerView.size());
        assertEquals(1, joinedMemberView.size());
        assertEquals(ownerView.get(0).getName(), joinedMemberView.get(0).getName());
        assertEquals(ownerView.get(0).getBarcode(), joinedMemberView.get(0).getBarcode());
        assertEquals(ownerView.get(0).getAmount(), joinedMemberView.get(0).getAmount());
    }

    // Issue #114 — verify g unit and kcalPer100g are persisted correctly
    @Test
    void addItem_success_withGramUnit_storesKcalPer100g() {
        Household household = new Household();
        household.setId(1L);

        PantryItemPostDTO postDTO = new PantryItemPostDTO();
        postDTO.setBarcode("1234567890");
        postDTO.setName("Oats");
        postDTO.setAmount(500.0);
        postDTO.setAmountUnit("g");
        postDTO.setKcalPer100g(380.0);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByHouseholdIdAndBarcode(eq(1L), any())).thenReturn(List.of());
        when(mockPantryRepo.save(any(PantryItem.class))).thenAnswer(inv -> {
            PantryItem saved = inv.getArgument(0);
            saved.setId(12L);
            return saved;
        });

        PantryItem result = pantryService.addItem(1L, postDTO, 99L);

        assertEquals(500.0, result.getAmount(), 0.001);
        assertEquals("g", result.getAmountUnit());
        assertEquals(380.0, result.getKcalPer100g(), 0.001);
        assertNull(result.getKcalPer100ml());
        assertNull(result.getKcalPerPackage());
    }

    // Issue #114 — amountUnit must be one of g, ml, package
    @Test
    void addItem_throwsException_whenAmountUnitIsInvalid() {
        PantryItemPostDTO postDTO = new PantryItemPostDTO();
        postDTO.setBarcode("1234567890");
        postDTO.setName("Oats");
        postDTO.setAmount(500.0);
        postDTO.setAmountUnit("oz");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> pantryService.addItem(1L, postDTO, 99L)
        );

        assertEquals("Amount unit must be one of: g, ml, package, serving.", exception.getMessage());
    }

    // Issue #114 — same barcode + same unit → accumulate amount, not create new row
    @Test
    void addItem_success_mergesAmount_whenSameUnitExists() {
        Household household = new Household();
        household.setId(1L);

        PantryItem existing = new PantryItem();
        existing.setId(10L);
        existing.setHouseholdId(1L);
        existing.setBarcode("1234567890");
        existing.setName("Oats");
        existing.setAmount(300.0);
        existing.setAmountUnit("g");
        existing.setKcalPer100g(380.0);
        existing.setAddedAt(java.time.Instant.now());

        PantryItemPostDTO postDTO = new PantryItemPostDTO();
        postDTO.setBarcode("1234567890");
        postDTO.setName("Oats");
        postDTO.setAmount(200.0);
        postDTO.setAmountUnit("g");
        postDTO.setKcalPer100g(380.0);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByHouseholdIdAndBarcode(eq(1L), eq("1234567890")))
            .thenReturn(List.of(existing));
        when(mockPantryRepo.save(any(PantryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        PantryItem result = pantryService.addItem(1L, postDTO, 99L);

        assertEquals(500.0, result.getAmount(), 0.001);
        assertEquals("g", result.getAmountUnit());
        assertEquals(10L, result.getId());  // same row, not a new one
    }

    // Issue #114 — same barcode but different unit → separate pantry row
    @Test
    void addItem_success_createsNewRow_whenUnitDiffers() {
        Household household = new Household();
        household.setId(1L);

        PantryItem existing = new PantryItem();
        existing.setId(10L);
        existing.setHouseholdId(1L);
        existing.setBarcode("1234567890");
        existing.setName("Oats");
        existing.setAmount(1.0);
        existing.setAmountUnit("package");
        existing.setKcalPerPackage(380.0);
        existing.setAddedAt(java.time.Instant.now());

        PantryItemPostDTO postDTO = new PantryItemPostDTO();
        postDTO.setBarcode("1234567890");
        postDTO.setName("Oats");
        postDTO.setAmount(500.0);
        postDTO.setAmountUnit("g");
        postDTO.setKcalPer100g(380.0);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByHouseholdIdAndBarcode(eq(1L), eq("1234567890")))
            .thenReturn(List.of(existing));
        when(mockPantryRepo.save(any(PantryItem.class))).thenAnswer(inv -> {
            PantryItem saved = inv.getArgument(0);
            if (saved.getId() == null) saved.setId(11L);
            return saved;
        });

        PantryItem result = pantryService.addItem(1L, postDTO, 99L);

        assertEquals(11L, result.getId());  // new row
        assertEquals(500.0, result.getAmount(), 0.001);
        assertEquals("g", result.getAmountUnit());
    }

    // Issue #121 — consuming on behalf of another member
    @Test
    void consumeItem_withConsumedForUserId_writesEffectiveConsumerAndActor() {
        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);
        item.setAmountUnit("package");
        item.setKcalPerPackage(100.0);
        item.setAmount(5.0);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(eq(new HouseholdMemberId(99L, 1L)))).thenReturn(true);
        when(mockHouseholdMemberRepo.existsById(eq(new HouseholdMemberId(77L, 1L)))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.of(item));

        pantryService.consumeItem(1L, 10L, 1.0, null, null, false, 99L, 77L);

        ArgumentCaptor<ConsumptionLog> captor = ArgumentCaptor.forClass(ConsumptionLog.class);
        verify(mockConsumptionRepo).save(captor.capture());
        assertEquals(77L, captor.getValue().getUserId());
        assertEquals(99L, captor.getValue().getActorUserId());
    }

    // Issue #121 — reject non-member as consumedForUserId
    @Test
    void consumeItem_withNonMemberConsumedForUserId_throwsException() {
        Household household = new Household();
        household.setId(1L);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(eq(new HouseholdMemberId(99L, 1L)))).thenReturn(true);
        when(mockHouseholdMemberRepo.existsById(eq(new HouseholdMemberId(55L, 1L)))).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> pantryService.consumeItem(1L, 10L, 1.0, null, null, false, 99L, 55L));
    }

    // Issue #114 — total calories must use unit-aware formula for all 3 unit types
    @Test
    void calculateTotalCalories_success_withMixedUnits() {
        PantryItem gramItem = new PantryItem();
        gramItem.setAmountUnit("g");
        gramItem.setAmount(250.0);
        gramItem.setKcalPer100g(200.0);   // 250 * 200 / 100 = 500 kcal

        PantryItem mlItem = new PantryItem();
        mlItem.setAmountUnit("ml");
        mlItem.setAmount(200.0);
        mlItem.setKcalPer100ml(50.0);     // 200 * 50 / 100 = 100 kcal

        PantryItem pkgItem = new PantryItem();
        pkgItem.setAmountUnit("package");
        pkgItem.setAmount(2.0);
        pkgItem.setKcalPerPackage(150.0); // 2 * 150 = 300 kcal

        when(mockPantryRepo.findByHouseholdId(1L))
            .thenReturn(List.of(gramItem, mlItem, pkgItem));

        double total = pantryService.calculateTotalCalories(1L);

        assertEquals(900.0, total, 0.001);
    }

    // --- calculateTotalCalories: serving unit and skip branches ---

    @Test
    void calculateTotalCalories_servingUnit_includesServingCalories() {
        PantryItem servingItem = new PantryItem();
        servingItem.setAmountUnit("serving");
        servingItem.setAmount(3.0);
        servingItem.setKcalPerServing(200.0); // 3 * 200 = 600

        when(mockPantryRepo.findByHouseholdId(1L)).thenReturn(List.of(servingItem));

        double total = pantryService.calculateTotalCalories(1L);

        assertEquals(600.0, total, 0.001);
    }

    @Test
    void calculateTotalCalories_itemWithNullUnit_isSkipped() {
        PantryItem noUnit = new PantryItem();
        noUnit.setAmountUnit(null);
        noUnit.setAmount(2.0);
        noUnit.setKcalPerPackage(100.0);

        PantryItem validItem = new PantryItem();
        validItem.setAmountUnit("package");
        validItem.setAmount(1.0);
        validItem.setKcalPerPackage(50.0);

        when(mockPantryRepo.findByHouseholdId(1L)).thenReturn(List.of(noUnit, validItem));

        double total = pantryService.calculateTotalCalories(1L);

        assertEquals(50.0, total, 0.001);
    }

    @Test
    void calculateTotalCalories_itemWithNullAmount_isSkipped() {
        PantryItem noAmount = new PantryItem();
        noAmount.setAmountUnit("package");
        noAmount.setAmount(null);
        noAmount.setKcalPerPackage(100.0);

        when(mockPantryRepo.findByHouseholdId(1L)).thenReturn(List.of(noAmount));

        double total = pantryService.calculateTotalCalories(1L);

        assertEquals(0.0, total, 0.001);
    }

    // --- getPantryItems: household not found ---

    @Test
    void getPantryItems_throwsException_whenHouseholdNotFound() {
        when(mockHouseholdRepo.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> pantryService.getPantryItems(99L, 1L)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // --- validatePantryItemPayload: null dto and blank barcode+name ---

    @Test
    void addItem_throwsException_whenDtoIsNull() {
        assertThrows(IllegalArgumentException.class, () -> pantryService.addItem(1L, null, 99L));
    }

    @Test
    void addItem_throwsException_whenBothBarcodeAndNameAreBlank() {
        PantryItemPostDTO postDTO = new PantryItemPostDTO();
        postDTO.setBarcode("   ");
        postDTO.setName("   ");
        postDTO.setAmount(1.0);
        postDTO.setAmountUnit("package");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.addItem(1L, postDTO, 99L)
        );

        assertEquals("Product name or barcode must not be empty.", ex.getMessage());
    }

    @Test
    void addItem_throwsException_whenAmountIsNull() {
        PantryItemPostDTO postDTO = new PantryItemPostDTO();
        postDTO.setBarcode("1234");
        postDTO.setName("Test");
        postDTO.setAmount(null);
        postDTO.setAmountUnit("package");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.addItem(1L, postDTO, 99L)
        );

        assertEquals("Amount must be greater than zero.", ex.getMessage());
    }

    // --- bulkAddItems: null entry in list ---

    @Test
    void bulkAddItems_throwsWhenListContainsNullEntry() {
        List<PantryItemPostDTO> items = new ArrayList<>();
        items.add(null);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.bulkAddItems(1L, items, 99L)
        );

        assertEquals("Bulk add items must not contain null entries.", ex.getMessage());
    }

    // --- bulkAddItems: household not found ---

    @Test
    void bulkAddItems_throwsWhenHouseholdNotFound() {
        PantryItemPostDTO dto = new PantryItemPostDTO();
        dto.setBarcode("111");
        dto.setName("A");
        dto.setAmount(1.0);
        dto.setAmountUnit("package");

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> pantryService.bulkAddItems(1L, List.of(dto), 99L)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // --- consumeItem: invalid amountUnit ---

    @Test
    void consumeItem_withInvalidAmountUnit_throwsException() {
        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);
        item.setAmountUnit("package");
        item.setAmount(5.0);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.of(item));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.consumeItem(1L, 10L, 1.0, "oz", null, false, 99L, null)
        );

        assertEquals("Amount unit must be one of: g, ml, serving, package.", ex.getMessage());
    }

    // --- consumeItem: serving unit ---

    @Test
    void consumeItem_withServingUnit_usesKcalPerServing() {
        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);
        item.setAmountUnit("serving");
        item.setKcalPerServing(300.0);
        item.setAmount(5.0);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.of(item));

        PantryService.ConsumeResult result = pantryService.consumeItem(1L, 10L, 2.0, 99L);

        assertEquals(3.0, result.getRemainingAmount(), 0.001);
        assertEquals(600.0, result.getConsumedCalories(), 0.001);
    }

    // --- ConsumeResult: deprecated getter/setter ---

    @Test
    void consumeResult_deprecatedGetRemainingCount_returnsIntValue() {
        PantryService.ConsumeResult result = new PantryService.ConsumeResult();
        result.setRemainingAmount(4.0);

        assertEquals(4, result.getRemainingCount());
    }

    @Test
    void consumeResult_deprecatedSetRemainingCount_setsDoubleValue() {
        PantryService.ConsumeResult result = new PantryService.ConsumeResult();
        result.setRemainingCount(7);

        assertEquals(7.0, result.getRemainingAmount(), 0.001);
    }

    @Test
    void consumeResult_deprecatedGetRemainingCount_nullWhenRemainingAmountNull() {
        PantryService.ConsumeResult result = new PantryService.ConsumeResult();
        result.setRemainingAmount(null);

        assertNull(result.getRemainingCount());
    }

    @Test
    void consumeResult_deprecatedSetRemainingCount_nullSetsNull() {
        PantryService.ConsumeResult result = new PantryService.ConsumeResult();
        result.setRemainingCount(null);

        assertNull(result.getRemainingAmount());
    }

    // --- estimateMealPortion ---

    @Test
    void estimateMealPortion_throwsWhenHouseholdNotFound() {
        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> pantryService.estimateMealPortion(1L, 10L, null, 99L)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void estimateMealPortion_throwsWhenUserIsNotMember() {
        Household household = new Household();
        household.setId(1L);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(false);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.estimateMealPortion(1L, 10L, null, 99L)
        );

        assertEquals("User is not a member of this household.", ex.getMessage());
    }

    @Test
    void estimateMealPortion_throwsWhenPantryItemNotFound() {
        Household household = new Household();
        household.setId(1L);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> pantryService.estimateMealPortion(1L, 10L, null, 99L)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void estimateMealPortion_delegatesToMealPortionEstimateService() {
        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);

        ch.uzh.ifi.hase.soprafs26.rest.dto.PortionEstimateResponseDTO expectedResponse =
                new ch.uzh.ifi.hase.soprafs26.rest.dto.PortionEstimateResponseDTO();

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.of(item));
        when(mockMealPortionEstimateService.estimatePortion(eq(item), any())).thenReturn(expectedResponse);

        ch.uzh.ifi.hase.soprafs26.rest.dto.PortionEstimateResponseDTO result =
                pantryService.estimateMealPortion(1L, 10L, null, 99L);

        assertEquals(expectedResponse, result);
        verify(mockMealPortionEstimateService).estimatePortion(eq(item), any());
    }

    // --- addItem: manualEntry flag bypasses local dataset ---

    @Test
    void addItem_withManualEntry_bypassesLocalDataset() {
        Household household = new Household();
        household.setId(1L);

        PantryItemPostDTO postDTO = new PantryItemPostDTO();
        postDTO.setBarcode("1234567890");
        postDTO.setName("Manual Product");
        postDTO.setAmount(1.0);
        postDTO.setAmountUnit("package");
        postDTO.setKcalPerPackage(100.0);
        postDTO.setManualEntry(true);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByHouseholdIdAndBarcode(eq(1L), any())).thenReturn(List.of());
        when(mockPantryRepo.save(any(PantryItem.class))).thenAnswer(inv -> {
            PantryItem saved = inv.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        PantryItem result = pantryService.addItem(1L, postDTO, 99L);

        // Even though we have a barcode, local dataset should not be consulted for name/kcal
        verify(mockLocalDatasetProductMapper, never()).toDto(any());
        assertEquals("Manual Product", result.getName());
        assertEquals(100.0, result.getKcalPerPackage(), 0.001);
    }

    // --- calculateTotalCalories: g item with null kcal ---

    @Test
    void calculateTotalCalories_gramItemWithNullKcal_doesNotContribute() {
        PantryItem item = new PantryItem();
        item.setAmountUnit("g");
        item.setAmount(200.0);
        item.setKcalPer100g(null);

        when(mockPantryRepo.findByHouseholdId(1L)).thenReturn(List.of(item));

        double total = pantryService.calculateTotalCalories(1L);

        assertEquals(0.0, total, 0.001);
    }

    // --- consumeItem: null amount throws ---

    @Test
    void consumeItem_nullAmount_throwsException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.consumeItem(1L, 10L, null, 99L)
        );

        assertEquals("Quantity must be greater than zero.", ex.getMessage());
    }

    // --- removeItem: null amount throws ---

    @Test
    void removeItem_nullAmount_throwsException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> pantryService.removeItem(1L, 10L, null, 99L)
        );

        assertEquals("Quantity must be greater than zero.", ex.getMessage());
    }

    // --- consumeItem: serving unit with no kcalPerServing returns null calories ---

    @Test
    void consumeItem_withServingUnit_nullKcalPerServing_returnsNullCalories() {
        Household household = new Household();
        household.setId(1L);

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);
        item.setAmountUnit("serving");
        item.setKcalPerServing(null);
        item.setAmount(4.0);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.of(item));

        PantryService.ConsumeResult result = pantryService.consumeItem(1L, 10L, 1.0, 99L);

        assertNull(result.getConsumedCalories());
        assertEquals(3.0, result.getRemainingAmount(), 0.001);
    }

    // --- computeConsumedCalories: g item with micronutrients uses kcalPer100g via basis ---

    @Test
    void consumeItem_withGramUnitAndMicronutrients_usesKcalPer100gViaBasis() {
        Household household = new Household();
        household.setId(1L);

        ch.uzh.ifi.hase.soprafs26.entity.PantryItemMicronutrients mn =
                new ch.uzh.ifi.hase.soprafs26.entity.PantryItemMicronutrients();
        mn.setNutritionBasisUnit("g");
        mn.setNutritionBasisAmount(java.math.BigDecimal.valueOf(100));

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);
        item.setAmountUnit("g");
        item.setKcalPer100g(400.0);
        item.setAmount(500.0);
        item.setMicronutrients(mn);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.of(item));

        // consume 200g: kcalPer100g * 200 / 100 = 800
        PantryService.ConsumeResult result = pantryService.consumeItem(1L, 10L, 200.0, 99L);

        assertEquals(800.0, result.getConsumedCalories(), 0.001);
        assertEquals(300.0, result.getRemainingAmount(), 0.001);
    }

    // --- computeConsumedCalories: ml item with micronutrients uses kcalPer100ml via basis ---

    @Test
    void consumeItem_withMlUnitAndMicronutrients_usesKcalPer100mlViaBasis() {
        Household household = new Household();
        household.setId(1L);

        ch.uzh.ifi.hase.soprafs26.entity.PantryItemMicronutrients mn =
                new ch.uzh.ifi.hase.soprafs26.entity.PantryItemMicronutrients();
        mn.setNutritionBasisUnit("ml");
        mn.setNutritionBasisAmount(java.math.BigDecimal.valueOf(100));

        PantryItem item = new PantryItem();
        item.setId(11L);
        item.setHouseholdId(1L);
        item.setAmountUnit("ml");
        item.setKcalPer100ml(60.0);
        item.setAmount(300.0);
        item.setMicronutrients(mn);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(11L, 1L)).thenReturn(Optional.of(item));

        // consume 100ml: kcalPer100ml * 100 / 100 = 60
        PantryService.ConsumeResult result = pantryService.consumeItem(1L, 11L, 100.0, 99L);

        assertEquals(60.0, result.getConsumedCalories(), 0.001);
        assertEquals(200.0, result.getRemainingAmount(), 0.001);
    }

    // --- computeConsumedCalories: basisAmount is zero, returns null ---

    @Test
    void consumeItem_withMicronutrientsButZeroBasisAmount_returnsNullCalories() {
        Household household = new Household();
        household.setId(1L);

        ch.uzh.ifi.hase.soprafs26.entity.PantryItemMicronutrients mn =
                new ch.uzh.ifi.hase.soprafs26.entity.PantryItemMicronutrients();
        mn.setNutritionBasisUnit("g");
        mn.setNutritionBasisAmount(java.math.BigDecimal.ZERO);

        PantryItem item = new PantryItem();
        item.setId(10L);
        item.setHouseholdId(1L);
        item.setAmountUnit("g");
        item.setKcalPer100g(400.0);
        item.setAmount(500.0);
        item.setMicronutrients(mn);

        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByIdAndHouseholdId(10L, 1L)).thenReturn(Optional.of(item));

        PantryService.ConsumeResult result = pantryService.consumeItem(1L, 10L, 100.0, 99L);

        assertNull(result.getConsumedCalories());
    }

    // --- calculateTotalCalories: ml unit with kcal ---

    @Test
    void calculateTotalCalories_mlItem_usesKcalPer100ml() {
        PantryItem item = new PantryItem();
        item.setAmountUnit("ml");
        item.setAmount(300.0);
        item.setKcalPer100ml(40.0); // 300 * 40 / 100 = 120

        when(mockPantryRepo.findByHouseholdId(1L)).thenReturn(List.of(item));

        double total = pantryService.calculateTotalCalories(1L);

        assertEquals(120.0, total, 0.001);
    }

    // ===== Local Dataset Product: calculateKcal* methods via addItem =====
    // These tests drive the private calculateKcalPerPackage / calculateKcalPerBasisUnit /
    // calculateKcalPerServing methods indirectly through addItem with a mocked local dataset match.

    private ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductDTO makeLocalProduct(
            String basisUnit, double basisAmount, double kcalValue,
            Double packageQuantity, String packageUnit,
            Double servingQuantity, String servingUnit) {
        ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductDTO p =
                new ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductDTO();
        p.setBarcode("1111111111");
        p.setName("Test Product");

        ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductDTO.NutritionDTO nutrition =
                new ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductDTO.NutritionDTO();
        nutrition.setBasisUnit(basisUnit);
        nutrition.setBasisAmount(basisAmount);
        ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductDTO.NutrientAmountDTO energy =
                new ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductDTO.NutrientAmountDTO(kcalValue, "kcal");
        nutrition.setCoreNutrition(java.util.Map.of("energy-kcal", energy));
        p.setNutrition(nutrition);

        p.setPackageQuantity(packageQuantity);
        p.setPackageQuantityUnit(packageUnit);
        p.setServingQuantity(servingQuantity);
        p.setServingQuantityUnit(servingUnit);

        return p;
    }

    private void setupHouseholdAndMember() {
        ch.uzh.ifi.hase.soprafs26.entity.Household household = new ch.uzh.ifi.hase.soprafs26.entity.Household();
        household.setId(1L);
        when(mockHouseholdRepo.findById(1L)).thenReturn(Optional.of(household));
        when(mockHouseholdMemberRepo.existsById(any(ch.uzh.ifi.hase.soprafs26.entity.HouseholdMemberId.class))).thenReturn(true);
        when(mockPantryRepo.findByHouseholdIdAndBarcode(eq(1L), any())).thenReturn(List.of());
        when(mockPantryRepo.save(any(PantryItem.class))).thenAnswer(inv -> {
            PantryItem saved = inv.getArgument(0);
            if (saved.getId() == null) saved.setId(50L);
            return saved;
        });
    }

    @Test
    void addItem_withLocalDatasetProduct_calculatesKcalPer100g() {
        setupHouseholdAndMember();

        ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductDTO localProduct =
                makeLocalProduct("g", 100.0, 400.0, null, null, null, null);

        ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetBucketScanner.class.getSimpleName(); // force class load

        // findRawRowByBarcode returns a non-empty Optional, then mapper returns localProduct
        when(mockLocalDatasetLookupService.findRawRowByBarcode(any())).thenReturn(Optional.of(java.util.Map.of("code", "1111111111")));
        when(mockLocalDatasetProductMapper.toDto(any())).thenReturn(localProduct);

        PantryItemPostDTO dto = new PantryItemPostDTO();
        dto.setBarcode("1111111111");
        dto.setName("Manual Name");
        dto.setAmount(1.0);
        dto.setAmountUnit("package");
        dto.setPackageQuantity("500");
        dto.setPackageQuantityUnit("g");

        PantryItem result = pantryService.addItem(1L, dto, 99L);

        // kcalPer100g = 400 * 100 / 100 = 400
        assertEquals(400.0, result.getKcalPer100g(), 0.001);
        assertNull(result.getKcalPer100ml());
    }

    @Test
    void addItem_withLocalDatasetProduct_calculatesKcalPer100ml() {
        setupHouseholdAndMember();

        ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductDTO localProduct =
                makeLocalProduct("ml", 100.0, 50.0, null, null, null, null);

        when(mockLocalDatasetLookupService.findRawRowByBarcode(any())).thenReturn(Optional.of(java.util.Map.of("code", "1111111111")));
        when(mockLocalDatasetProductMapper.toDto(any())).thenReturn(localProduct);

        PantryItemPostDTO dto = new PantryItemPostDTO();
        dto.setBarcode("1111111111");
        dto.setName("Juice");
        dto.setAmount(1.0);
        dto.setAmountUnit("package");
        dto.setPackageQuantity("500");
        dto.setPackageQuantityUnit("ml");

        PantryItem result = pantryService.addItem(1L, dto, 99L);

        // kcalPer100ml = 50 * 100 / 100 = 50
        assertEquals(50.0, result.getKcalPer100ml(), 0.001);
        assertNull(result.getKcalPer100g());
    }

    @Test
    void addItem_withLocalDatasetProduct_calculatesKcalPerPackage() {
        setupHouseholdAndMember();

        // basis=g, packageUnit=g, packageQuantity=200 → kcalPerPackage = 400 * 200 / 100 = 800
        ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductDTO localProduct =
                makeLocalProduct("g", 100.0, 400.0, 200.0, "g", null, null);

        when(mockLocalDatasetLookupService.findRawRowByBarcode(any())).thenReturn(Optional.of(java.util.Map.of("code", "1111111111")));
        when(mockLocalDatasetProductMapper.toDto(any())).thenReturn(localProduct);

        PantryItemPostDTO dto = new PantryItemPostDTO();
        dto.setBarcode("1111111111");
        dto.setName("Biscuits");
        dto.setAmount(1.0);
        dto.setAmountUnit("package");

        PantryItem result = pantryService.addItem(1L, dto, 99L);

        assertEquals(800.0, result.getKcalPerPackage(), 0.001);
    }

    @Test
    void addItem_withLocalDatasetProduct_calculatesKcalPerServing() {
        setupHouseholdAndMember();

        // basis=g basisAmount=100, kcal=300, serving=50g → kcalPerServing = 300 * 50 / 100 = 150
        ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductDTO localProduct =
                makeLocalProduct("g", 100.0, 300.0, null, null, 50.0, "g");

        when(mockLocalDatasetLookupService.findRawRowByBarcode(any())).thenReturn(Optional.of(java.util.Map.of("code", "1111111111")));
        when(mockLocalDatasetProductMapper.toDto(any())).thenReturn(localProduct);

        PantryItemPostDTO dto = new PantryItemPostDTO();
        dto.setBarcode("1111111111");
        dto.setName("Granola");
        dto.setAmount(1.0);
        dto.setAmountUnit("package");
        dto.setPackageQuantity("200");
        dto.setPackageQuantityUnit("g");

        PantryItem result = pantryService.addItem(1L, dto, 99L);

        assertEquals(150.0, result.getKcalPerServing(), 0.001);
    }

    @Test
    void addItem_withLocalDatasetProduct_whenDtoPackageUnitMismatchesBasis_throws() {
        setupHouseholdAndMember();

        // basisUnit=g, no usable package info on product
        ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductDTO localProduct =
                makeLocalProduct("g", 100.0, 400.0, null, null, null, null);

        when(mockLocalDatasetLookupService.findRawRowByBarcode(any())).thenReturn(Optional.of(java.util.Map.of("code", "1111111111")));
        when(mockLocalDatasetProductMapper.toDto(any())).thenReturn(localProduct);

        PantryItemPostDTO dto = new PantryItemPostDTO();
        dto.setBarcode("1111111111");
        dto.setName("Something");
        dto.setAmount(1.0);
        dto.setAmountUnit("package");
        dto.setPackageQuantity("200");
        dto.setPackageQuantityUnit("ml"); // mismatches basisUnit "g"

        assertThrows(IllegalArgumentException.class, () -> pantryService.addItem(1L, dto, 99L));
    }

    @Test
    void addItem_withLocalDatasetProduct_kcalPerServing_nullWhenServingUnitMismatch() {
        setupHouseholdAndMember();

        // servingQuantityUnit=ml but basisUnit=g → mismatch → null
        ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductDTO localProduct =
                makeLocalProduct("g", 100.0, 300.0, null, null, 50.0, "ml");

        when(mockLocalDatasetLookupService.findRawRowByBarcode(any())).thenReturn(Optional.of(java.util.Map.of("code", "1111111111")));
        when(mockLocalDatasetProductMapper.toDto(any())).thenReturn(localProduct);

        PantryItemPostDTO dto = new PantryItemPostDTO();
        dto.setBarcode("1111111111");
        dto.setName("Granola");
        dto.setAmount(1.0);
        dto.setAmountUnit("package");
        dto.setPackageQuantity("200");
        dto.setPackageQuantityUnit("g");

        PantryItem result = pantryService.addItem(1L, dto, 99L);

        assertNull(result.getKcalPerServing());
    }
}
