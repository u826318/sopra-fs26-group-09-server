package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class LocalDatasetProductMapper {
  private static final String DATA_SOURCE = "local_dataset";

  private static final Map<String, String> INDEX_TO_NUTRIENT_KEY = Map.ofEntries(
      Map.entry("0", "energy-kcal"),
      Map.entry("1", "fat"),
      Map.entry("2", "saturated-fat"),
      Map.entry("3", "carbohydrates"),
      Map.entry("4", "sugars"),
      Map.entry("5", "fiber"),
      Map.entry("6", "proteins"),
      Map.entry("7", "salt"),
      Map.entry("8", "sodium"),
      Map.entry("9", "calcium"),
      Map.entry("10", "choline"),
      Map.entry("11", "copper"),
      Map.entry("12", "iodine"),
      Map.entry("13", "iron"),
      Map.entry("14", "magnesium"),
      Map.entry("15", "manganese"),
      Map.entry("16", "phosphorus"),
      Map.entry("17", "potassium"),
      Map.entry("18", "selenium"),
      Map.entry("19", "zinc"),
      Map.entry("20", "vitamin-a"),
      Map.entry("21", "vitamin-b1"),
      Map.entry("22", "vitamin-b2"),
      Map.entry("23", "vitamin-b6"),
      Map.entry("24", "vitamin-b12"),
      Map.entry("25", "vitamin-c"),
      Map.entry("26", "vitamin-d"),
      Map.entry("27", "vitamin-e"),
      Map.entry("28", "vitamin-k"),
      Map.entry("29", "vitamin-b9"),
      Map.entry("30", "vitamin-pp"),
      Map.entry("31", "pantothenic-acid"),
      Map.entry("32", "biotin"),
      Map.entry("33", "chloride"),
      Map.entry("34", "chromium"),
      Map.entry("35", "fluoride"),
      Map.entry("36", "molybdenum")
  );

  private static final Set<String> CORE_NUTRIENT_KEYS = Set.of(
      "energy-kcal",
      "fat",
      "saturated-fat",
      "carbohydrates",
      "sugars",
      "fiber",
      "proteins",
      "salt"
  );

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final LocalDatasetImageUrlBuilder imageUrlBuilder;

  public LocalDatasetProductMapper(LocalDatasetImageUrlBuilder imageUrlBuilder) {
    this.imageUrlBuilder = imageUrlBuilder;
  }

  public LocalDatasetProductDTO toDto(Map<String, String> row) {
    LocalDatasetProductDTO dto = new LocalDatasetProductDTO();

    String barcode = cleanToNull(row.get("code"));
    String basisUnit = normalizeUnit(row.get("nutrition_basis_unit"));

    dto.setProductIndex(parseLong(row.get("product_index")));
    dto.setBarcode(barcode);
    dto.setName(firstNameCandidate(row.get("name_candidates")));
    dto.setBrand(firstBrand(row.get("brands")));
    dto.setImageUrl(imageUrlBuilder.buildBestImageUrl(
        barcode,
        row.get("image_1"),
        row.get("image_2")
    ));

    dto.setProductQuantity(cleanToNull(row.get("product_quantity")));
    dto.setProductQuantityUnit(normalizeUnit(row.get("product_quantity_unit")));

    dto.setPackageQuantity(parseDouble(row.get("package_quantity")));
    dto.setPackageQuantityUnit(normalizeUnit(row.get("package_quantity_unit")));

    dto.setServingQuantity(parseDouble(row.get("serving_quantity")));
    dto.setServingQuantityUnit(normalizeUnit(row.get("serving_quantity_unit")));

    dto.setNutrition(decodeNutrition(row.get("nutrition"), basisUnit));
    dto.setConsumptionOptions(buildConsumptionOptions(dto, basisUnit));
    dto.setDataSource(DATA_SOURCE);

    return dto;
  }

  private LocalDatasetProductDTO.NutritionDTO decodeNutrition(
      String nutritionJson,
      String basisUnit
  ) {
    LocalDatasetProductDTO.NutritionDTO nutrition = new LocalDatasetProductDTO.NutritionDTO();
    nutrition.setBasisAmount(100.0);
    nutrition.setBasisUnit(basisUnit);
    nutrition.setCoreNutrition(new LinkedHashMap<>());
    nutrition.setMicronutrients(new LinkedHashMap<>());

    if (nutritionJson == null || nutritionJson.isBlank()) {
      return nutrition;
    }

    try {
      JsonNode root = objectMapper.readTree(nutritionJson);

      if (!root.isObject()) {
        return nutrition;
      }

      Iterator<Map.Entry<String, JsonNode>> fields = root.fields();

      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> entry = fields.next();
        String nutrientIndex = entry.getKey();
        JsonNode valueNode = entry.getValue();

        if (!valueNode.isNumber()) {
          continue;
        }

        String nutrientKey = INDEX_TO_NUTRIENT_KEY.get(nutrientIndex);

        if (nutrientKey == null) {
          continue;
        }

        LocalDatasetProductDTO.NutrientAmountDTO amount =
            new LocalDatasetProductDTO.NutrientAmountDTO(
                valueNode.asDouble(),
                unitForNutrient(nutrientKey)
            );

        if (CORE_NUTRIENT_KEYS.contains(nutrientKey)) {
          nutrition.getCoreNutrition().put(nutrientKey, amount);
        }
        else {
          nutrition.getMicronutrients().put(nutrientKey, amount);
        }
      }
    }
    catch (Exception ignored) {
      return nutrition;
    }

    return nutrition;
  }

  private List<LocalDatasetProductDTO.ConsumptionOptionDTO> buildConsumptionOptions(
      LocalDatasetProductDTO dto,
      String basisUnit
  ) {
    List<LocalDatasetProductDTO.ConsumptionOptionDTO> options = new ArrayList<>();

    if ("g".equals(basisUnit)) {
      options.add(new LocalDatasetProductDTO.ConsumptionOptionDTO("GRAMS", "grams", "g"));
    }
    else if ("ml".equals(basisUnit)) {
      options.add(new LocalDatasetProductDTO.ConsumptionOptionDTO("MILLILITERS", "milliliters", "ml"));
    }

    if (hasPositiveQuantity(dto.getPackageQuantity())
        && basisUnit != null
        && basisUnit.equals(dto.getPackageQuantityUnit())) {
      options.add(new LocalDatasetProductDTO.ConsumptionOptionDTO("PACKAGE", "package", "package"));
    }

    if (hasPositiveQuantity(dto.getServingQuantity())
        && basisUnit != null
        && basisUnit.equals(dto.getServingQuantityUnit())) {
      options.add(new LocalDatasetProductDTO.ConsumptionOptionDTO("SERVINGS", "servings", "serving"));
    }

    return options;
  }

  private boolean hasPositiveQuantity(Double value) {
    return value != null && value > 0;
  }

  private String unitForNutrient(String nutrientKey) {
    if ("energy-kcal".equals(nutrientKey)) {
      return "kcal";
    }

    if (CORE_NUTRIENT_KEYS.contains(nutrientKey)) {
      return "g";
    }

    return "µg";
  }

  private String firstNameCandidate(String rawNameCandidates) {
    String cleanedRaw = cleanToNull(rawNameCandidates);

    if (cleanedRaw == null) {
      return null;
    }

    try {
      JsonNode root = objectMapper.readTree(cleanedRaw);

      if (root.isArray() && !root.isEmpty()) {
        return cleanToNull(root.get(0).asText());
      }

      if (root.isTextual()) {
        return cleanToNull(root.asText());
      }
    }
    catch (Exception ignored) {
      return cleanedRaw;
    }

    return null;
  }

  private String firstBrand(String brands) {
    String cleaned = cleanToNull(brands);

    if (cleaned == null) {
      return null;
    }

    return cleanToNull(cleaned.split("[,;/|]")[0]);
  }

  private Long parseLong(String value) {
    String cleaned = cleanToNull(value);

    if (cleaned == null) {
      return null;
    }

    try {
      return Long.parseLong(cleaned);
    }
    catch (NumberFormatException ignored) {
      return null;
    }
  }

  private Double parseDouble(String value) {
    String cleaned = cleanToNull(value);

    if (cleaned == null) {
      return null;
    }

    try {
      return Double.parseDouble(cleaned);
    }
    catch (NumberFormatException ignored) {
      return null;
    }
  }

  private String normalizeUnit(String value) {
    String cleaned = cleanToNull(value);

    if (cleaned == null) {
      return null;
    }

    String lower = cleaned.toLowerCase();

    if ("milliliter".equals(lower) || "millilitre".equals(lower)) {
      return "ml";
    }

    if ("gram".equals(lower) || "grams".equals(lower)) {
      return "g";
    }

    return lower;
  }

  private String cleanToNull(String value) {
    if (value == null) {
      return null;
    }

    String cleaned = value.trim();
    return cleaned.isBlank() ? null : cleaned;
  }
}
