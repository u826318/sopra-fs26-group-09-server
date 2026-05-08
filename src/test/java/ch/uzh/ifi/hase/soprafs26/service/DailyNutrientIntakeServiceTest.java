package ch.uzh.ifi.hase.soprafs26.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.uzh.ifi.hase.soprafs26.entity.DailyNutrientIntake;
import ch.uzh.ifi.hase.soprafs26.entity.PantryItem;
import ch.uzh.ifi.hase.soprafs26.entity.PantryItemMicronutrients;
import ch.uzh.ifi.hase.soprafs26.repository.DailyNutrientIntakeRepository;
import ch.uzh.ifi.hase.soprafs26.repository.PantryItemMicronutrientsRepository;

class DailyNutrientIntakeServiceTest {

    private DailyNutrientIntakeRepository dailyRepository;
    private PantryItemMicronutrientsRepository micronutrientsRepository;
    private DailyNutrientIntakeService service;

    @BeforeEach
    void setUp() {
        dailyRepository = mock(DailyNutrientIntakeRepository.class);
        micronutrientsRepository = mock(PantryItemMicronutrientsRepository.class);
        service = new DailyNutrientIntakeService(dailyRepository, micronutrientsRepository);

        when(dailyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void getDailyIntakeOrEmpty_returnsExistingIntake() {
        LocalDate date = LocalDate.of(2026, 5, 8);
        DailyNutrientIntake existing = new DailyNutrientIntake();
        when(dailyRepository.findByUserIdAndIntakeDate(1L, date)).thenReturn(Optional.of(existing));

        DailyNutrientIntake result = service.getDailyIntakeOrEmpty(1L, date);

        assertSame(existing, result);
    }

    @Test
    void getDailyIntakeOrEmpty_createsEmptyIntakeWhenMissing() {
        LocalDate date = LocalDate.of(2026, 5, 8);
        when(dailyRepository.findByUserIdAndIntakeDate(1L, date)).thenReturn(Optional.empty());

        DailyNutrientIntake result = service.getDailyIntakeOrEmpty(1L, date);

        assertEquals(1L, result.getUserId());
        assertEquals(date, result.getIntakeDate());
    }

    @Test
    void recordConsumedPantryItem_addsMicronutrientsToExistingDailyIntake() {
        PantryItem pantryItem = pantryItem(10L);
        PantryItemMicronutrients micronutrients = micronutrients();
        DailyNutrientIntake existing = new DailyNutrientIntake();
        existing.setBiotin(new BigDecimal("1.000000"));
        existing.setCalcium(new BigDecimal("2.000000"));

        LocalDate intakeDate = LocalDate.of(2026, 5, 8);
        Instant consumedAt = Instant.parse("2026-05-08T10:15:30Z");

        when(micronutrientsRepository.findByPantryItemId(10L)).thenReturn(Optional.of(micronutrients));
        when(dailyRepository.findByUserIdAndIntakeDate(3L, intakeDate)).thenReturn(Optional.of(existing));

        DailyNutrientIntake result = service.recordConsumedPantryItem(3L, pantryItem, 2, consumedAt);

        assertSame(existing, result);
        verify(dailyRepository).save(existing);
        assertBigDecimalEquals("3.000000", result.getBiotin());
        assertBigDecimalEquals("6.000000", result.getCalcium());
        assertBigDecimalEquals("6.000000", result.getChloride());
        assertBigDecimalEquals("8.000000", result.getCholine());
        assertBigDecimalEquals("10.000000", result.getChromium());
        assertBigDecimalEquals("12.000000", result.getCopper());
        assertBigDecimalEquals("14.000000", result.getFluoride());
        assertBigDecimalEquals("16.000000", result.getFolate());
        assertBigDecimalEquals("18.000000", result.getIodine());
        assertBigDecimalEquals("20.000000", result.getIron());
        assertBigDecimalEquals("22.000000", result.getMagnesium());
        assertBigDecimalEquals("24.000000", result.getManganese());
        assertBigDecimalEquals("26.000000", result.getMolybdenum());
        assertBigDecimalEquals("28.000000", result.getNiacin());
        assertBigDecimalEquals("30.000000", result.getPantothenicAcid());
        assertBigDecimalEquals("32.000000", result.getPhosphorus());
        assertBigDecimalEquals("34.000000", result.getPotassium());
        assertBigDecimalEquals("36.000000", result.getRiboflavin());
        assertBigDecimalEquals("38.000000", result.getSelenium());
        assertBigDecimalEquals("40.000000", result.getSodium());
        assertBigDecimalEquals("42.000000", result.getThiamin());
        assertBigDecimalEquals("44.000000", result.getVitaminA());
        assertBigDecimalEquals("46.000000", result.getVitaminB12());
        assertBigDecimalEquals("48.000000", result.getVitaminB6());
        assertBigDecimalEquals("50.000000", result.getVitaminC());
        assertBigDecimalEquals("52.000000", result.getVitaminD());
        assertBigDecimalEquals("54.000000", result.getVitaminE());
        assertBigDecimalEquals("56.000000", result.getVitaminK());
        assertBigDecimalEquals("58.000000", result.getZinc());
    }

    @Test
    void recordConsumedPantryItem_createsDailyIntakeWhenMissing() {
        PantryItem pantryItem = pantryItem(10L);
        PantryItemMicronutrients micronutrients = new PantryItemMicronutrients();
        micronutrients.setIron(new BigDecimal("1.250000"));
        LocalDate intakeDate = LocalDate.of(2026, 5, 8);

        when(micronutrientsRepository.findByPantryItemId(10L)).thenReturn(Optional.of(micronutrients));
        when(dailyRepository.findByUserIdAndIntakeDate(3L, intakeDate)).thenReturn(Optional.empty());

        DailyNutrientIntake result = service.recordConsumedPantryItem(
                3L,
                pantryItem,
                4,
                Instant.parse("2026-05-08T12:00:00Z")
        );

        assertEquals(3L, result.getUserId());
        assertEquals(intakeDate, result.getIntakeDate());
        assertBigDecimalEquals("5.000000", result.getIron());
    }

    @Test
    void recordConsumedPantryItem_returnsNullForInvalidInputOrMissingMicronutrients() {
        assertNull(service.recordConsumedPantryItem(null, pantryItem(1L), 1, Instant.now()));
        assertNull(service.recordConsumedPantryItem(1L, null, 1, Instant.now()));
        assertNull(service.recordConsumedPantryItem(1L, pantryItem(null), 1, Instant.now()));
        assertNull(service.recordConsumedPantryItem(1L, pantryItem(1L), null, Instant.now()));
        assertNull(service.recordConsumedPantryItem(1L, pantryItem(1L), 0, Instant.now()));

        when(micronutrientsRepository.findByPantryItemId(1L)).thenReturn(Optional.empty());
        assertNull(service.recordConsumedPantryItem(1L, pantryItem(1L), 1, Instant.now()));

        verify(dailyRepository, never()).save(any());
    }

    private PantryItem pantryItem(Long id) {
        PantryItem pantryItem = new PantryItem();
        pantryItem.setId(id);
        return pantryItem;
    }

    private PantryItemMicronutrients micronutrients() {
        PantryItemMicronutrients micronutrients = new PantryItemMicronutrients();
        micronutrients.setBiotin(new BigDecimal("1"));
        micronutrients.setCalcium(new BigDecimal("2"));
        micronutrients.setChloride(new BigDecimal("3"));
        micronutrients.setCholine(new BigDecimal("4"));
        micronutrients.setChromium(new BigDecimal("5"));
        micronutrients.setCopper(new BigDecimal("6"));
        micronutrients.setFluoride(new BigDecimal("7"));
        micronutrients.setFolate(new BigDecimal("8"));
        micronutrients.setIodine(new BigDecimal("9"));
        micronutrients.setIron(new BigDecimal("10"));
        micronutrients.setMagnesium(new BigDecimal("11"));
        micronutrients.setManganese(new BigDecimal("12"));
        micronutrients.setMolybdenum(new BigDecimal("13"));
        micronutrients.setNiacin(new BigDecimal("14"));
        micronutrients.setPantothenicAcid(new BigDecimal("15"));
        micronutrients.setPhosphorus(new BigDecimal("16"));
        micronutrients.setPotassium(new BigDecimal("17"));
        micronutrients.setRiboflavin(new BigDecimal("18"));
        micronutrients.setSelenium(new BigDecimal("19"));
        micronutrients.setSodium(new BigDecimal("20"));
        micronutrients.setThiamin(new BigDecimal("21"));
        micronutrients.setVitaminA(new BigDecimal("22"));
        micronutrients.setVitaminB12(new BigDecimal("23"));
        micronutrients.setVitaminB6(new BigDecimal("24"));
        micronutrients.setVitaminC(new BigDecimal("25"));
        micronutrients.setVitaminD(new BigDecimal("26"));
        micronutrients.setVitaminE(new BigDecimal("27"));
        micronutrients.setVitaminK(new BigDecimal("28"));
        micronutrients.setZinc(new BigDecimal("29"));
        return micronutrients;
    }

    private void assertBigDecimalEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
