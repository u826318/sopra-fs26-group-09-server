package ch.uzh.ifi.hase.soprafs26.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ch.uzh.ifi.hase.soprafs26.entity.PantryItem;
import ch.uzh.ifi.hase.soprafs26.entity.PantryItemMicronutrients;
import ch.uzh.ifi.hase.soprafs26.repository.PantryItemMicronutrientsRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.PantryItemMicronutrientPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductDTO;

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

    // upsertMicronutrientsPerBasisFromLocalDataset

    @Test
    void upsertMicronutrientsPerBasisFromLocalDataset_savesNutritionBasisAndMicronutrients() {
        PantryItem item = pantryItem(10L);
        when(repository.findByPantryItemId(10L)).thenReturn(Optional.empty());

        LocalDatasetProductDTO product = new LocalDatasetProductDTO();
        product.setPackageQuantity(500.0);
        product.setPackageQuantityUnit("g");
        product.setServingQuantity(null);
        product.setServingQuantityUnit(null);

        LocalDatasetProductDTO.NutritionDTO nutrition = new LocalDatasetProductDTO.NutritionDTO();
        nutrition.setBasisAmount(100.0);
        nutrition.setBasisUnit("g");
        nutrition.setCoreNutrition(new LinkedHashMap<>());
        Map<String, LocalDatasetProductDTO.NutrientAmountDTO> micros = new LinkedHashMap<>();
        micros.put("calcium", new LocalDatasetProductDTO.NutrientAmountDTO(120.0, "µg"));
        micros.put("iron", new LocalDatasetProductDTO.NutrientAmountDTO(2.0, "mg"));
        nutrition.setMicronutrients(micros);
        product.setNutrition(nutrition);

        service.upsertMicronutrientsPerBasisFromLocalDataset(item, product);

        ArgumentCaptor<PantryItemMicronutrients> captor = ArgumentCaptor.forClass(PantryItemMicronutrients.class);
        verify(repository).save(captor.capture());

        PantryItemMicronutrients saved = captor.getValue();
        assertSame(item, saved.getPantryItem());
        assertEquals("g", saved.getNutritionBasisUnit());
        assertBigDecimalEquals("100", saved.getNutritionBasisAmount());
        assertBigDecimalEquals("120.000000", saved.getCalcium());
        assertBigDecimalEquals("2000.000000", saved.getIron());
        assertBigDecimalEquals("500", saved.getPackageGrams());
    }

    @Test
    void upsertMicronutrientsPerBasisFromLocalDataset_skipsNullItem() {
        service.upsertMicronutrientsPerBasisFromLocalDataset(null, new LocalDatasetProductDTO());
        verify(repository, never()).save(any());
    }

    @Test
    void upsertMicronutrientsPerBasisFromLocalDataset_skipsItemWithNullId() {
        service.upsertMicronutrientsPerBasisFromLocalDataset(pantryItem(null), new LocalDatasetProductDTO());
        verify(repository, never()).save(any());
    }

    @Test
    void upsertMicronutrientsPerBasisFromLocalDataset_skipsNullProduct() {
        service.upsertMicronutrientsPerBasisFromLocalDataset(pantryItem(1L), null);
        verify(repository, never()).save(any());
    }

    @Test
    void upsertMicronutrientsPerBasisFromLocalDataset_nullNutrition_stillSaves() {
        PantryItem item = pantryItem(11L);
        when(repository.findByPantryItemId(11L)).thenReturn(Optional.empty());

        LocalDatasetProductDTO product = new LocalDatasetProductDTO();
        product.setNutrition(null);

        service.upsertMicronutrientsPerBasisFromLocalDataset(item, product);

        verify(repository).save(any());
    }

    // upsertManualMicronutrientsPerBasis

    @Test
    void upsertManualMicronutrientsPerBasis_gBasis_setsNutritionBasisAndSaves() {
        PantryItem item = pantryItem(20L);
        when(repository.findByPantryItemId(20L)).thenReturn(Optional.empty());

        PantryItemMicronutrientPostDTO ironDto = new PantryItemMicronutrientPostDTO();
        ironDto.setValue(5.0);
        ironDto.setUnit("mg");

        service.upsertManualMicronutrientsPerBasis(item, "g", Map.of("iron", ironDto));

        ArgumentCaptor<PantryItemMicronutrients> captor = ArgumentCaptor.forClass(PantryItemMicronutrients.class);
        verify(repository).save(captor.capture());

        PantryItemMicronutrients saved = captor.getValue();
        assertEquals("g", saved.getNutritionBasisUnit());
        assertBigDecimalEquals("100", saved.getNutritionBasisAmount());
        assertBigDecimalEquals("5000.000000", saved.getIron());
    }

    @Test
    void upsertManualMicronutrientsPerBasis_packageBasis_setsPackageQuantity() {
        PantryItem item = pantryItem(21L);
        when(repository.findByPantryItemId(21L)).thenReturn(Optional.empty());

        PantryItemMicronutrientPostDTO calciumDto = new PantryItemMicronutrientPostDTO();
        calciumDto.setValue(200.0);
        calciumDto.setUnit("µg");

        service.upsertManualMicronutrientsPerBasis(item, "package", Map.of("calcium", calciumDto));

        ArgumentCaptor<PantryItemMicronutrients> captor = ArgumentCaptor.forClass(PantryItemMicronutrients.class);
        verify(repository).save(captor.capture());

        PantryItemMicronutrients saved = captor.getValue();
        assertEquals("package", saved.getNutritionBasisUnit());
        assertEquals("1 package", saved.getPackageQuantity());
        assertBigDecimalEquals("200.000000", saved.getCalcium());
    }

    @Test
    void upsertManualMicronutrientsPerBasis_mlBasis_setsNutritionBasisUnit() {
        PantryItem item = pantryItem(22L);
        when(repository.findByPantryItemId(22L)).thenReturn(Optional.empty());

        PantryItemMicronutrientPostDTO sodiumDto = new PantryItemMicronutrientPostDTO();
        sodiumDto.setValue(1.0);
        sodiumDto.setUnit("g");

        service.upsertManualMicronutrientsPerBasis(item, "ml", Map.of("sodium", sodiumDto));

        ArgumentCaptor<PantryItemMicronutrients> captor = ArgumentCaptor.forClass(PantryItemMicronutrients.class);
        verify(repository).save(captor.capture());

        assertEquals("ml", captor.getValue().getNutritionBasisUnit());
    }

    @Test
    void upsertManualMicronutrientsPerBasis_invalidUnit_skips() {
        service.upsertManualMicronutrientsPerBasis(pantryItem(1L), "kg", Map.of("iron", new PantryItemMicronutrientPostDTO()));
        verify(repository, never()).save(any());
    }

    @Test
    void upsertManualMicronutrientsPerBasis_nullItem_skips() {
        service.upsertManualMicronutrientsPerBasis(null, "g", Map.of());
        verify(repository, never()).save(any());
    }

    @Test
    void upsertManualMicronutrientsPerBasis_emptyMicronutrients_skips() {
        service.upsertManualMicronutrientsPerBasis(pantryItem(1L), "g", Map.of());
        verify(repository, never()).save(any());
    }

    @Test
    void upsertManualMicronutrientsPerBasis_updatesExistingRecord() {
        PantryItem item = pantryItem(23L);
        PantryItemMicronutrients existing = new PantryItemMicronutrients();
        existing.setId(77L);
        when(repository.findByPantryItemId(23L)).thenReturn(Optional.of(existing));

        PantryItemMicronutrientPostDTO zincDto = new PantryItemMicronutrientPostDTO();
        zincDto.setValue(3.0);
        zincDto.setUnit("mg");

        service.upsertManualMicronutrientsPerBasis(item, "g", Map.of("zinc", zincDto));

        verify(repository).save(existing);
        assertEquals(77L, existing.getId());
        assertBigDecimalEquals("3000.000000", existing.getZinc());
    }

    private void assertBigDecimalEquals(String expected, BigDecimal actual) {
        assertNotNull(actual, "Expected " + expected + " but got null");
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
