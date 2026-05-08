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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ch.uzh.ifi.hase.soprafs26.entity.PantryItem;
import ch.uzh.ifi.hase.soprafs26.entity.PantryItemMicronutrients;
import ch.uzh.ifi.hase.soprafs26.repository.PantryItemMicronutrientsRepository;

class PantryItemMicronutrientServiceTest {

    private PantryItemMicronutrientsRepository repository;
    private PantryItemMicronutrientService service;

    @BeforeEach
    void setUp() {
        repository = mock(PantryItemMicronutrientsRepository.class);
        service = new PantryItemMicronutrientService(repository);
    }

    @Test
    void upsertMicronutrientsPerPackage_createsRecordAndConvertsSupportedUnits() {
        PantryItem pantryItem = pantryItem(42L);
        Map<String, Object> nutriments = fullNutriments();

        when(repository.findByPantryItemId(42L)).thenReturn(Optional.empty());

        service.upsertMicronutrientsPerPackage(pantryItem, "2 x 250g", nutriments);

        ArgumentCaptor<PantryItemMicronutrients> captor = ArgumentCaptor.forClass(PantryItemMicronutrients.class);
        verify(repository).save(captor.capture());

        PantryItemMicronutrients saved = captor.getValue();
        assertSame(pantryItem, saved.getPantryItem());
        assertSame(saved, pantryItem.getMicronutrients());
        assertEquals("2 x 250g", saved.getPackageQuantity());
        assertBigDecimalEquals("500", saved.getPackageGrams());
        assertBigDecimalEquals("5000000.000000", saved.getBiotin());
        assertBigDecimalEquals("10000.000000", saved.getCalcium());
        assertBigDecimalEquals("15.000000", saved.getChloride());
        assertBigDecimalEquals("50000.000000", saved.getCholine());
        assertBigDecimalEquals("5000000.000000", saved.getFolate());
        assertBigDecimalEquals("5000000.000000", saved.getVitaminK());
        assertNull(saved.getZinc());
    }

    @Test
    void upsertMicronutrientsPerPackage_updatesExistingRecord() {
        PantryItem pantryItem = pantryItem(7L);
        PantryItemMicronutrients existing = new PantryItemMicronutrients();
        existing.setId(99L);
        Map<String, Object> nutriments = Map.of("iron_100g", "0.25");

        when(repository.findByPantryItemId(7L)).thenReturn(Optional.of(existing));

        service.upsertMicronutrientsPerPackage(pantryItem, "1kg", nutriments);

        verify(repository).save(existing);
        assertEquals(99L, existing.getId());
        assertSame(pantryItem, existing.getPantryItem());
        assertBigDecimalEquals("1000", existing.getPackageGrams());
        assertBigDecimalEquals("2500000.000000", existing.getIron());
    }

    @Test
    void upsertMicronutrientsPerPackage_skipsInvalidInput() {
        service.upsertMicronutrientsPerPackage(null, "100g", Map.of("iron_100g", "1"));
        service.upsertMicronutrientsPerPackage(pantryItem(null), "100g", Map.of("iron_100g", "1"));
        service.upsertMicronutrientsPerPackage(pantryItem(1L), "100g", null);
        service.upsertMicronutrientsPerPackage(pantryItem(1L), "100g", Map.of());
        service.upsertMicronutrientsPerPackage(pantryItem(1L), "no weight", Map.of("iron_100g", "1"));
        service.upsertMicronutrientsPerPackage(pantryItem(1L), "0g", Map.of("iron_100g", "1"));

        verify(repository, never()).save(any());
    }

    @Test
    void upsertMicronutrientsPerPackage_parsesCommaDecimalsAndMilligrams() {
        PantryItem pantryItem = pantryItem(5L);
        when(repository.findByPantryItemId(5L)).thenReturn(Optional.empty());

        service.upsertMicronutrientsPerPackage(
                pantryItem,
                "250,5mg",
                Map.of("selenium_value", "2,5", "selenium_unit", "mcg")
        );

        ArgumentCaptor<PantryItemMicronutrients> captor = ArgumentCaptor.forClass(PantryItemMicronutrients.class);
        verify(repository).save(captor.capture());

        PantryItemMicronutrients saved = captor.getValue();
        assertBigDecimalEquals("0.250500", saved.getPackageGrams());
        assertBigDecimalEquals("0.006263", saved.getSelenium());
    }

    private PantryItem pantryItem(Long id) {
        PantryItem pantryItem = new PantryItem();
        pantryItem.setId(id);
        return pantryItem;
    }

    private Map<String, Object> fullNutriments() {
        Map<String, Object> nutriments = new HashMap<>();
        nutriments.put("biotin_100g", "1");
        nutriments.put("calcium_value", "2");
        nutriments.put("calcium_unit", "mg");
        nutriments.put("chloride_value", "3");
        nutriments.put("chloride_unit", "ug");
        nutriments.put("choline_value", "0.01");
        nutriments.put("choline_unit", "g");
        nutriments.put("chromium_unit", "IU");
        nutriments.put("vitamin-b9_100g", "1");
        nutriments.put("phylloquinone_100g", "1");

        for (String key : otherMappedNutrientKeys()) {
            nutriments.put(key + "_100g", "1");
        }

        return nutriments;
    }

    private String[] otherMappedNutrientKeys() {
        return new String[] {
                "copper",
                "fluoride",
                "iodine",
                "iron",
                "magnesium",
                "manganese",
                "molybdenum",
                "vitamin-pp",
                "pantothenic-acid",
                "phosphorus",
                "potassium",
                "vitamin-b2",
                "selenium",
                "sodium",
                "vitamin-b1",
                "vitamin-a",
                "vitamin-b12",
                "vitamin-b6",
                "vitamin-c",
                "vitamin-d",
                "vitamin-e"
        };
    }

    private void assertBigDecimalEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
