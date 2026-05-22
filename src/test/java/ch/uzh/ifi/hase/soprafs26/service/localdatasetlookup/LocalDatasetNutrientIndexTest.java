package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalDatasetNutrientIndexTest {

    @Test
    void index_containsEnergyKcal() {
        assertEquals("energy-kcal", LocalDatasetNutrientIndex.INDEX_TO_NUTRIENT_KEY.get("0"));
    }

    @Test
    void index_containsCoreNutrients() {
        assertEquals("fat", LocalDatasetNutrientIndex.INDEX_TO_NUTRIENT_KEY.get("2"));
        assertEquals("carbohydrates", LocalDatasetNutrientIndex.INDEX_TO_NUTRIENT_KEY.get("4"));
        assertEquals("proteins", LocalDatasetNutrientIndex.INDEX_TO_NUTRIENT_KEY.get("7"));
        assertEquals("salt", LocalDatasetNutrientIndex.INDEX_TO_NUTRIENT_KEY.get("8"));
    }

    @Test
    void index_containsMinerals() {
        assertEquals("calcium", LocalDatasetNutrientIndex.INDEX_TO_NUTRIENT_KEY.get("10"));
        assertEquals("iron", LocalDatasetNutrientIndex.INDEX_TO_NUTRIENT_KEY.get("14"));
        assertEquals("zinc", LocalDatasetNutrientIndex.INDEX_TO_NUTRIENT_KEY.get("20"));
    }

    @Test
    void index_containsVitamins() {
        assertEquals("vitamin-a", LocalDatasetNutrientIndex.INDEX_TO_NUTRIENT_KEY.get("21"));
        assertEquals("vitamin-c", LocalDatasetNutrientIndex.INDEX_TO_NUTRIENT_KEY.get("26"));
        assertEquals("vitamin-d", LocalDatasetNutrientIndex.INDEX_TO_NUTRIENT_KEY.get("27"));
        assertEquals("vitamin-b12", LocalDatasetNutrientIndex.INDEX_TO_NUTRIENT_KEY.get("25"));
    }

    @Test
    void index_containsBiotinAndChloride() {
        assertEquals("biotin", LocalDatasetNutrientIndex.INDEX_TO_NUTRIENT_KEY.get("33"));
        assertEquals("chloride", LocalDatasetNutrientIndex.INDEX_TO_NUTRIENT_KEY.get("34"));
        assertEquals("molybdenum", LocalDatasetNutrientIndex.INDEX_TO_NUTRIENT_KEY.get("37"));
    }

    @Test
    void index_unknownKeyReturnsNull() {
        assertNull(LocalDatasetNutrientIndex.INDEX_TO_NUTRIENT_KEY.get("999"));
    }

    @Test
    void index_hasExpectedSize() {
        assertEquals(38, LocalDatasetNutrientIndex.INDEX_TO_NUTRIENT_KEY.size());
    }
}
