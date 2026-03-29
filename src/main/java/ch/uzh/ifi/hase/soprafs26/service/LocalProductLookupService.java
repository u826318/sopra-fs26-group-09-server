package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.rest.dto.ProductDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

@Service
public class LocalProductLookupService {

  private static final String DEFAULT_OFF_JSONL_DATASET_PATH = "/home/liyifu/SoPra26/off-data/openfoodfacts-swiss-or-chains-filtered.jsonl.gz";
  private static final String OFF_JSONL_DATASET_PATH =
      System.getenv().getOrDefault("OFF_JSONL_DATASET_PATH", DEFAULT_OFF_JSONL_DATASET_PATH);

  private final ObjectMapper objectMapper = new ObjectMapper();

  public ProductDTO lookupByBarcode(String barcode) {
    String safeBarcode = blankToNull(barcode);
    if (safeBarcode == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "barcode must not be empty");
    }

    Path datasetPath = Path.of(OFF_JSONL_DATASET_PATH);
    if (!Files.exists(datasetPath)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY,
          "Local dataset file not found: " + OFF_JSONL_DATASET_PATH
      );
    }

    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(
            new GZIPInputStream(Files.newInputStream(datasetPath)),
            StandardCharsets.UTF_8
        )
    )) {
      String line;
      while ((line = reader.readLine()) != null) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
          continue;
        }

        Map<String, Object> row = objectMapper.readValue(
            trimmed,
            new TypeReference<Map<String, Object>>() {}
        );

        String code = firstNonBlank(
            asString(row.get("code")),
            asString(row.get("_id")),
            asString(row.get("id"))
        );

        if (safeBarcode.equals(code)) {
          return mapRow(row, safeBarcode);
        }
      }
    }
    catch (ResponseStatusException e) {
      throw e;
    }
    catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY,
          "Local dataset lookup failed: " + e.getMessage(),
          e
      );
    }

    throw new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "No product found for barcode " + safeBarcode
    );
  }

  private ProductDTO mapRow(Map<String, Object> row, String fallbackBarcode) {
    ProductDTO dto = new ProductDTO();

    dto.setBarcode(firstNonBlank(
        asString(row.get("code")),
        asString(row.get("barcode")),
        asString(row.get("id")),
        asString(row.get("_id")),
        fallbackBarcode
    ));

    dto.setName(firstNonBlank(
        asString(row.get("product_name")),
        asString(row.get("product_name_en")),
        asString(row.get("product_name_de")),
        asString(row.get("product_name_fr")),
        asString(row.get("abbreviated_product_name")),
        asString(row.get("name")),
        "Unknown product"
    ));

    dto.setBrand(firstNonBlank(
        asString(row.get("brands")),
        asString(row.get("brand")),
        asString(row.get("brands_en"))
    ));

    dto.setQuantity(firstNonBlank(
        asString(row.get("quantity")),
        asString(row.get("product_quantity"))
    ));

    dto.setServingSize(firstNonBlank(
        asString(row.get("serving_size")),
        asString(row.get("serving_quantity"))
    ));

    dto.setImageUrl(firstNonBlank(
        asString(row.get("image_front_url")),
        asString(row.get("image_url")),
        asString(row.get("image_front_small_url"))
    ));

    dto.setProductUrl(firstNonBlank(
        asString(row.get("url")),
        asString(row.get("link"))
    ));

    dto.setNutriScore(firstNonBlank(
        asString(row.get("nutrition_grades")),
        asString(row.get("nutriscore_grade"))
    ));

    dto.setStores(asStringList(row.get("stores")));
    dto.setStoreTags(asStringList(row.get("stores_tags")));
    dto.setPurchasePlaces(asStringList(firstPresent(row, "purchase_places", "purchase_places_tags")));

    Map<String, Object> nutriments = asMap(row.get("nutriments"));
    if (nutriments == null || nutriments.isEmpty()) {
      nutriments = buildNutrimentsFromNutrition(row);
    }
    if (nutriments == null || nutriments.isEmpty()) {
      nutriments = buildFlatNutriments(row);
    }
    dto.setNutriments(nutriments);

    dto.setNutriScoreData(asMap(firstPresent(row, "nutriscore_data", "nutriScoreData")));
    dto.setRawProduct(row);

    return dto;
  }

  private Map<String, Object> buildNutrimentsFromNutrition(Map<String, Object> row) {
    Map<String, Object> nutrition = asMap(row.get("nutrition"));
    if (nutrition == null || nutrition.isEmpty()) {
      return null;
    }

    Map<String, Object> aggregatedSet = asMap(nutrition.get("aggregated_set"));
    if (aggregatedSet != null) {
      Map<String, Object> nutrients = asMap(aggregatedSet.get("nutrients"));
      if (nutrients != null && !nutrients.isEmpty()) {
        Map<String, Object> flattened = flattenNutritionEntries(nutrients, asString(aggregatedSet.get("per")));
        if (!flattened.isEmpty()) {
          return flattened;
        }
      }
    }

    Object inputSetsValue = nutrition.get("input_sets");
    if (inputSetsValue instanceof Collection<?> inputSets) {
      for (Object inputSetValue : inputSets) {
        Map<String, Object> inputSet = asMap(inputSetValue);
        if (inputSet == null) {
          continue;
        }
        Map<String, Object> nutrients = asMap(inputSet.get("nutrients"));
        if (nutrients == null || nutrients.isEmpty()) {
          continue;
        }
        Map<String, Object> flattened = flattenNutritionEntries(nutrients, asString(inputSet.get("per")));
        if (!flattened.isEmpty()) {
          return flattened;
        }
      }
    }

    return null;
  }

  private Map<String, Object> flattenNutritionEntries(Map<String, Object> nutrients, String per) {
    Map<String, Object> flattened = new LinkedHashMap<>();
    String suffix = null;

    if ("100g".equalsIgnoreCase(per) || "100ml".equalsIgnoreCase(per)) {
      suffix = "_" + per.toLowerCase();
    }
    else if ("serving".equalsIgnoreCase(per)) {
      suffix = "_serving";
    }

    for (Map.Entry<String, Object> entry : nutrients.entrySet()) {
      String nutrientName = entry.getKey();
      Map<String, Object> nutrientData = asMap(entry.getValue());
      if (nutrientData == null || nutrientData.isEmpty()) {
        continue;
      }

      Object value = firstPresent(nutrientData, "value", "value_computed", "value_string");
      Object unit = nutrientData.get("unit");

      if (value != null) {
        flattened.put(nutrientName, value);
        if (suffix != null) {
          flattened.put(nutrientName + suffix, value);
        }
      }
      if (unit != null) {
        flattened.put(nutrientName + "_unit", unit);
      }
    }

    return flattened;
  }

  private Map<String, Object> buildFlatNutriments(Map<String, Object> row) {
    Map<String, Object> nutriments = new LinkedHashMap<>();

    String[] keys = {
        "energy-kcal", "energy-kcal_100g", "energy-kcal_100ml", "energy-kcal_serving",
        "energy-kj", "energy-kj_100g", "energy-kj_100ml", "energy-kj_serving",
        "energy", "energy_100g", "energy_100ml", "energy_serving",
        "fat", "fat_100g", "fat_100ml", "fat_serving",
        "saturated-fat", "saturated-fat_100g", "saturated-fat_100ml", "saturated-fat_serving",
        "carbohydrates", "carbohydrates_100g", "carbohydrates_100ml", "carbohydrates_serving",
        "sugars", "sugars_100g", "sugars_100ml", "sugars_serving",
        "fiber", "fiber_100g", "fiber_100ml", "fiber_serving",
        "proteins", "proteins_100g", "proteins_100ml", "proteins_serving",
        "salt", "salt_100g", "salt_100ml", "salt_serving",
        "sodium", "sodium_100g", "sodium_100ml", "sodium_serving",
        "nova-group", "nova-group_100g", "nova-group_serving",
        "carbohydrates_unit", "fat_unit", "fiber_unit", "proteins_unit",
        "salt_unit", "sodium_unit", "sugars_unit", "energy-kcal_unit", "energy-kj_unit"
    };

    for (String key : keys) {
      Object value = row.get(key);
      if (value != null) {
        nutriments.put(key, value);
      }
    }

    return nutriments.isEmpty() ? null : nutriments;
  }

  private Object firstPresent(Map<String, Object> row, String... keys) {
    for (String key : keys) {
      if (row.containsKey(key) && row.get(key) != null) {
        return row.get(key);
      }
    }
    return null;
  }

  private Map<String, Object> asMap(Object value) {
    if (value == null) {
      return null;
    }

    if (value instanceof Map<?, ?> map) {
      Map<String, Object> out = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        out.put(String.valueOf(entry.getKey()), entry.getValue());
      }
      return out;
    }

    if (value instanceof String text) {
      try {
        return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {});
      }
      catch (Exception ignored) {
        return null;
      }
    }

    return null;
  }

  private List<String> asStringList(Object value) {
    LinkedHashSet<String> out = new LinkedHashSet<>();

    if (value == null) {
      return new ArrayList<>();
    }

    if (value instanceof Collection<?> collection) {
      for (Object item : collection) {
        String s = blankToNull(String.valueOf(item));
        if (s != null) {
          out.add(s);
        }
      }
      return new ArrayList<>(out);
    }

    String text = blankToNull(String.valueOf(value));
    if (text == null) {
      return new ArrayList<>();
    }

    for (String piece : text.split(",")) {
      String s = blankToNull(piece);
      if (s != null) {
        out.add(s);
      }
    }

    return new ArrayList<>(out);
  }

  private String asString(Object value) {
    return value == null ? null : blankToNull(String.valueOf(value));
  }

  private String firstNonBlank(String... candidates) {
    for (String candidate : candidates) {
      String value = blankToNull(candidate);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
