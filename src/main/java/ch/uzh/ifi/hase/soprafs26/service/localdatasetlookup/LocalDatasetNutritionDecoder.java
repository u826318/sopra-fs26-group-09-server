package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

@Component
public class LocalDatasetNutritionDecoder {

  private final ObjectMapper objectMapper = new ObjectMapper();

  public ObjectNode decodeNutrition(String nutritionJson) {
    ObjectNode result = objectMapper.createObjectNode();
    ObjectNode nutrients = objectMapper.createObjectNode();

    result.set("nutrients", nutrients);

    if (nutritionJson == null || nutritionJson.isBlank()) {
      return result;
    }

    try {
      JsonNode root = objectMapper.readTree(nutritionJson);

      if (!root.isObject()) {
        return result;
      }

      Iterator<Map.Entry<String, JsonNode>> fields = root.fields();

      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();

        String nutrientIndex = field.getKey();
        String nutrientKey = LocalDatasetNutrientIndex.INDEX_TO_NUTRIENT_KEY.get(nutrientIndex);

        if (nutrientKey == null) {
          continue;
        }

        JsonNode valueAndUnit = field.getValue();

        if (!valueAndUnit.isArray() || valueAndUnit.size() < 2) {
          continue;
        }

        JsonNode valueNode = valueAndUnit.get(0);
        JsonNode unitNode = valueAndUnit.get(1);

        if (!valueNode.isNumber()) {
          continue;
        }

        ObjectNode nutrient = objectMapper.createObjectNode();
        nutrient.put("value", valueNode.asDouble());
        nutrient.put("unit", unitNode.isTextual() ? unitNode.asText() : "");

        nutrients.set(nutrientKey, nutrient);
      }

      return result;
    }
    catch (IOException e) {
      return result;
    }
  }
}