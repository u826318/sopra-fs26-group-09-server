package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LocalDatasetImageUrlBuilder {

  private static final String BASE_URL = "https://images.openfoodfacts.org/images/products";
  private static final String DEFAULT_RESOLUTION = "400";

  private final ObjectMapper objectMapper = new ObjectMapper();

  public String buildFromImage1(String barcode, String image1Json) {
    Map<String, String> revisionsByLanguage = parseRevisionMap(image1Json);
    return buildSelectedFrontImageUrl(barcode, revisionsByLanguage);
  }

  public String buildFromImage2(String barcode, String image2Json) {
    Map<String, String> revisionsByLanguage = parseRevisionMap(image2Json);
    return buildSelectedFrontImageUrl(barcode, revisionsByLanguage);
  }

  public String buildBestImageUrl(String barcode, String image1Json, String image2Json) {
    String image1Url = buildFromImage1(barcode, image1Json);

    if (image1Url != null) {
      return image1Url;
    }

    return buildFromImage2(barcode, image2Json);
  }

  private String buildSelectedFrontImageUrl(
      String barcode,
      Map<String, String> revisionsByLanguage
  ) {
    if (barcode == null || barcode.isBlank() || revisionsByLanguage.isEmpty()) {
      return null;
    }

    Map.Entry<String, String> selectedEntry = pickPreferredRevision(revisionsByLanguage);

    if (selectedEntry == null) {
      return null;
    }

    String language = selectedEntry.getKey();
    String revision = selectedEntry.getValue();

    if (revision == null || revision.isBlank()) {
      return null;
    }

    String imageName = "default".equals(language)
        ? "front"
        : "front_" + language;

    String productFolder = buildProductImageFolder(barcode);

    if (productFolder == null) {
      return null;
    }

    return BASE_URL + "/"
        + productFolder + "/"
        + imageName + "."
        + revision + "."
        + DEFAULT_RESOLUTION
        + ".jpg";
  }

  private Map.Entry<String, String> pickPreferredRevision(Map<String, String> revisionsByLanguage) {
    List<String> preferredLanguages = List.of("en", "de", "fr", "it", "default");

    for (String language : preferredLanguages) {
      if (revisionsByLanguage.containsKey(language)) {
        return Map.entry(language, revisionsByLanguage.get(language));
      }
    }

    return revisionsByLanguage.entrySet().stream()
        .findFirst()
        .orElse(null);
  }

  private String buildProductImageFolder(String barcode) {
    String digitsOnly = barcode.trim();

    if (!digitsOnly.matches("\\d+")) {
      return null;
    }

    if (digitsOnly.length() < 13) {
      digitsOnly = "0".repeat(13 - digitsOnly.length()) + digitsOnly;
    }

    if (digitsOnly.length() < 10) {
      return null;
    }

    return digitsOnly.substring(0, 3)
        + "/"
        + digitsOnly.substring(3, 6)
        + "/"
        + digitsOnly.substring(6, 9)
        + "/"
        + digitsOnly.substring(9);
  }

  private Map<String, String> parseRevisionMap(String json) {
    Map<String, String> revisions = new LinkedHashMap<>();

    if (json == null || json.isBlank()) {
      return revisions;
    }

    try {
      JsonNode root = objectMapper.readTree(json);

      if (!root.isObject()) {
        return revisions;
      }

      Iterator<Map.Entry<String, JsonNode>> fields = root.fields();

      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();

        String language = field.getKey();
        JsonNode revisionNode = field.getValue();

        String revision = revisionNode.isTextual()
            ? revisionNode.asText()
            : revisionNode.asText(null);

        if (language != null && !language.isBlank()
            && revision != null && !revision.isBlank()) {
          revisions.put(language, revision);
        }
      }
    }
    catch (Exception e) {
      return revisions;
    }

    return revisions;
  }
}