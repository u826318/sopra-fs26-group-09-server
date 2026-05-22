package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LocalDatasetProductMapperTest {

    private LocalDatasetImageUrlBuilder imageUrlBuilder;
    private LocalDatasetProductMapper mapper;

    @BeforeEach
    void setUp() {
        imageUrlBuilder = mock(LocalDatasetImageUrlBuilder.class);
        mapper = new LocalDatasetProductMapper(imageUrlBuilder);
    }

    private Map<String, String> basicRow() {
        Map<String, String> row = new HashMap<>();
        row.put("product_index", "42");
        row.put("code", "3017624010701");
        row.put("name_candidates", "[\"Nutella\"]");
        row.put("brands", "Ferrero");
        row.put("nutrition_basis_unit", "g");
        return row;
    }

    @Test
    void toDto_mapsBasicFields() {
        when(imageUrlBuilder.buildBestImageUrl(any(), any(), any())).thenReturn("http://img.example.com/test.jpg");

        LocalDatasetProductDTO dto = mapper.toDto(basicRow());

        assertEquals(42L, dto.getProductIndex());
        assertEquals("3017624010701", dto.getBarcode());
        assertEquals("Nutella", dto.getName());
        assertEquals("Ferrero", dto.getBrand());
        assertEquals("local_dataset", dto.getDataSource());
    }

    @Test
    void toDto_setsImageUrlFromBuilder() {
        when(imageUrlBuilder.buildBestImageUrl(eq("3017624010701"), any(), any())).thenReturn("http://img.test/img.jpg");

        LocalDatasetProductDTO dto = mapper.toDto(basicRow());

        assertEquals("http://img.test/img.jpg", dto.getImageUrl());
    }

    @Test
    void toDto_parsesNutritionCoreAndMicronutrients() {
        Map<String, String> row = basicRow();
        // index 0 = energy-kcal (core), index 9 = calcium (micro)
        row.put("nutrition", "{\"0\":250.0,\"9\":120.0}");

        LocalDatasetProductDTO dto = mapper.toDto(row);

        assertNotNull(dto.getNutrition());
        assertNotNull(dto.getNutrition().getCoreNutrition().get("energy-kcal"));
        assertEquals(250.0, dto.getNutrition().getCoreNutrition().get("energy-kcal").getValue());
        assertEquals("kcal", dto.getNutrition().getCoreNutrition().get("energy-kcal").getUnit());

        assertNotNull(dto.getNutrition().getMicronutrients().get("calcium"));
        assertEquals(120.0, dto.getNutrition().getMicronutrients().get("calcium").getValue());
        assertEquals("µg", dto.getNutrition().getMicronutrients().get("calcium").getUnit());
    }

    @Test
    void toDto_coreNutrientFat_hasGramUnit() {
        Map<String, String> row = basicRow();
        // index 1 = fat (core, non-energy)
        row.put("nutrition", "{\"1\":10.5}");

        LocalDatasetProductDTO dto = mapper.toDto(row);

        assertNotNull(dto.getNutrition().getCoreNutrition().get("fat"));
        assertEquals("g", dto.getNutrition().getCoreNutrition().get("fat").getUnit());
    }

    @Test
    void toDto_nullNutritionJson_returnsEmptyNutrition() {
        Map<String, String> row = basicRow();
        row.put("nutrition", null);

        LocalDatasetProductDTO dto = mapper.toDto(row);

        assertNotNull(dto.getNutrition());
        assertTrue(dto.getNutrition().getCoreNutrition().isEmpty());
        assertTrue(dto.getNutrition().getMicronutrients().isEmpty());
    }

    @Test
    void toDto_invalidNutritionJson_returnsEmptyNutrition() {
        Map<String, String> row = basicRow();
        row.put("nutrition", "not-valid-json");

        LocalDatasetProductDTO dto = mapper.toDto(row);

        assertNotNull(dto.getNutrition());
        assertTrue(dto.getNutrition().getCoreNutrition().isEmpty());
    }

    @Test
    void toDto_nameFromJsonArray() {
        Map<String, String> row = basicRow();
        row.put("name_candidates", "[\"First Name\",\"Second Name\"]");

        LocalDatasetProductDTO dto = mapper.toDto(row);

        assertEquals("First Name", dto.getName());
    }

    @Test
    void toDto_nameFromPlainString() {
        Map<String, String> row = basicRow();
        row.put("name_candidates", "\"Plain Name\"");

        LocalDatasetProductDTO dto = mapper.toDto(row);

        assertEquals("Plain Name", dto.getName());
    }

    @Test
    void toDto_nameNullWhenMissing() {
        Map<String, String> row = basicRow();
        row.put("name_candidates", null);

        LocalDatasetProductDTO dto = mapper.toDto(row);

        assertNull(dto.getName());
    }

    @Test
    void toDto_nameFromInvalidJson_fallsBackToRawValue() {
        Map<String, String> row = basicRow();
        row.put("name_candidates", "Raw Name Text");

        LocalDatasetProductDTO dto = mapper.toDto(row);

        assertEquals("Raw Name Text", dto.getName());
    }

    @Test
    void toDto_brandFirstBeforeComma() {
        Map<String, String> row = basicRow();
        row.put("brands", "Nestle, KitKat, Other");

        LocalDatasetProductDTO dto = mapper.toDto(row);

        assertEquals("Nestle", dto.getBrand());
    }

    @Test
    void toDto_brandFirstBeforeSemicolon() {
        Map<String, String> row = basicRow();
        row.put("brands", "Ferrero; Nutella");

        LocalDatasetProductDTO dto = mapper.toDto(row);

        assertEquals("Ferrero", dto.getBrand());
    }

    @Test
    void toDto_normalizeUnit_milliliterToMl() {
        Map<String, String> row = basicRow();
        row.put("nutrition_basis_unit", "milliliter");
        row.put("package_quantity_unit", "millilitre");

        LocalDatasetProductDTO dto = mapper.toDto(row);

        assertEquals("ml", dto.getNutrition().getBasisUnit());
        assertEquals("ml", dto.getPackageQuantityUnit());
    }

    @Test
    void toDto_normalizeUnit_gramsToG() {
        Map<String, String> row = basicRow();
        row.put("nutrition_basis_unit", "grams");

        LocalDatasetProductDTO dto = mapper.toDto(row);

        assertEquals("g", dto.getNutrition().getBasisUnit());
    }

    @Test
    void toDto_consumptionOptions_gramsAddedWhenBasisIsG() {
        Map<String, String> row = basicRow();
        row.put("nutrition_basis_unit", "g");

        LocalDatasetProductDTO dto = mapper.toDto(row);

        assertTrue(dto.getConsumptionOptions().stream().anyMatch(opt -> "GRAMS".equals(opt.getType())));
    }

    @Test
    void toDto_consumptionOptions_millilitersAddedWhenBasisIsMl() {
        Map<String, String> row = basicRow();
        row.put("nutrition_basis_unit", "ml");

        LocalDatasetProductDTO dto = mapper.toDto(row);

        assertTrue(dto.getConsumptionOptions().stream().anyMatch(opt -> "MILLILITERS".equals(opt.getType())));
    }

    @Test
    void toDto_consumptionOptions_packageAddedWhenQuantityMatchesBasis() {
        Map<String, String> row = basicRow();
        row.put("nutrition_basis_unit", "g");
        row.put("package_quantity", "500");
        row.put("package_quantity_unit", "g");

        LocalDatasetProductDTO dto = mapper.toDto(row);

        assertTrue(dto.getConsumptionOptions().stream().anyMatch(opt -> "PACKAGE".equals(opt.getType())));
    }

    @Test
    void toDto_consumptionOptions_packageNotAddedWhenUnitMismatch() {
        Map<String, String> row = basicRow();
        row.put("nutrition_basis_unit", "g");
        row.put("package_quantity", "500");
        row.put("package_quantity_unit", "ml");

        LocalDatasetProductDTO dto = mapper.toDto(row);

        assertFalse(dto.getConsumptionOptions().stream().anyMatch(opt -> "PACKAGE".equals(opt.getType())));
    }

    @Test
    void toDto_invalidNutrientIndexIgnored() {
        Map<String, String> row = basicRow();
        row.put("nutrition", "{\"999\":100.0}");

        LocalDatasetProductDTO dto = mapper.toDto(row);

        assertTrue(dto.getNutrition().getCoreNutrition().isEmpty());
        assertTrue(dto.getNutrition().getMicronutrients().isEmpty());
    }

    @Test
    void toDto_nonNumericValueInNutritionIgnored() {
        Map<String, String> row = basicRow();
        row.put("nutrition", "{\"0\":\"not-a-number\"}");

        LocalDatasetProductDTO dto = mapper.toDto(row);

        assertFalse(dto.getNutrition().getCoreNutrition().containsKey("energy-kcal"));
    }
}
