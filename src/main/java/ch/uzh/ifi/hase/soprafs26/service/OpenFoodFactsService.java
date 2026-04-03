package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.rest.dto.ProductDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OpenFoodFactsService {

  private static final Logger log = LoggerFactory.getLogger(OpenFoodFactsService.class);

  private static final String OFF_BASE = "https://world.openfoodfacts.net";
  private static final String USER_AGENT = "sopra-fs26-group-09-virtual-pantry/0.1 (OpenFoodFacts portal)";
  private static final int MAX_LIMIT = 12;

  private final RestTemplate restTemplate = new RestTemplate();
  private final ObjectMapper objectMapper = new ObjectMapper();

  public ProductDTO lookupByBarcode(String barcode) {
    String sanitizedBarcode = blankToNull(barcode);
    if (sanitizedBarcode == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "barcode must not be empty");
    }

    String url = OFF_BASE + "/api/v2/product/" + urlEncode(sanitizedBarcode);
    String body = getWithUserAgent(url);

    try {
      JsonNode root = objectMapper.readTree(body);
      int status = root.path("status").asInt(0);
      if (status != 1) {
        throw new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "OpenFoodFacts: product not found for barcode " + sanitizedBarcode
        );
      }

      JsonNode product = root.path("product");
      return mapProduct(product, sanitizedBarcode);
    }
    catch (ResponseStatusException e) {
      throw e;
    }
    catch (Exception e) {
      log.error("Failed to parse OpenFoodFacts response", e);
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY,
          "Failed to parse OpenFoodFacts response",
          e
      );
    }
  }

  public List<ProductDTO> search(String query, int limit) {
    String sanitizedQuery = blankToNull(query);
    if (sanitizedQuery == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "q must not be empty");
    }

    int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
    String url = OFF_BASE + "/cgi/search.pl?search_terms=" + urlEncode(sanitizedQuery)
        + "&search_simple=1&action=process&json=1&page_size=" + safeLimit;

    String body = getWithUserAgent(url);

    try {
      JsonNode root = objectMapper.readTree(body);
      JsonNode products = root.path("products");
      List<ProductDTO> results = new ArrayList<>();
      Set<String> seenBarcodes = new LinkedHashSet<>();

      if (products.isArray()) {
        for (JsonNode hit : products) {
          String barcode = blankToNull(hit.path("code").asText(null));
          if (barcode == null || !seenBarcodes.add(barcode)) {
            continue;
          }

          try {
            results.add(lookupByBarcode(barcode));
          }
          catch (ResponseStatusException ex) {
            log.warn("Falling back to partial search hit for barcode {}: {}", barcode, ex.getReason());
            results.add(mapProduct(hit, barcode));
          }
        }
      }

      return results;
    }
    catch (ResponseStatusException e) {
      throw e;
    }
    catch (Exception e) {
      log.error("Failed to parse OpenFoodFacts search response", e);
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY,
          "Failed to parse OpenFoodFacts search response",
          e
      );
    }
  }

  private ProductDTO mapProduct(JsonNode product, String fallbackBarcode) {
    ProductDTO dto = new ProductDTO();
    dto.setBarcode(firstNonBlank(
        product.path("code").asText(null),
        fallbackBarcode
    ));
    dto.setName(firstNonBlank(
        product.path("product_name").asText(null),
        product.path("abbreviated_product_name").asText(null),
        "Unknown product"
    ));
    dto.setBrand(blankToNull(product.path("brands").asText(null)));
    dto.setQuantity(blankToNull(product.path("quantity").asText(null)));
    dto.setServingSize(blankToNull(product.path("serving_size").asText(null)));
    dto.setImageUrl(firstNonBlank(
        product.path("image_front_url").asText(null),
        product.path("image_url").asText(null)
    ));
    dto.setProductUrl(blankToNull(product.path("url").asText(null)));
    dto.setNutriScore(blankToNull(product.path("nutrition_grades").asText(null)));

    dto.setStores(extractStringList(product, "stores"));
    dto.setStoreTags(extractStringList(product, "stores_tags"));
    dto.setPurchasePlaces(extractStringList(product, "purchase_places", "purchase_places_tags"));

    dto.setNutriments(copyObjectNodeOrNull(product.get("nutriments")));
    dto.setNutriScoreData(copyObjectNodeOrNull(product.get("nutriscore_data")));
    dto.setRawProduct(copyObjectNodeOrNull(product));

    return dto;
  }

  private String getWithUserAgent(String url) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
      HttpEntity<Void> entity = new HttpEntity<>(headers);
      ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
      if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            "OpenFoodFacts request failed: " + response.getStatusCode()
        );
      }
      return response.getBody();
    }
    catch (ResponseStatusException e) {
      throw e;
    }
    catch (RestClientException e) {
      log.error("OpenFoodFacts request failed", e);
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY,
          "OpenFoodFacts request failed",
          e
      );
    }
  }

  private List<String> extractStringList(JsonNode product, String... fieldNames) {
    LinkedHashSet<String> values = new LinkedHashSet<>();

    for (String fieldName : fieldNames) {
      JsonNode node = product.get(fieldName);
      if (node == null || node.isNull() || node.isMissingNode()) {
        continue;
      }

      if (node.isArray()) {
        for (JsonNode item : node) {
          String value = blankToNull(item.asText(null));
          if (value != null) {
            values.add(value);
          }
        }
        continue;
      }

      if (node.isTextual()) {
        String text = node.asText();
        for (String piece : text.split(",")) {
          String value = blankToNull(piece);
          if (value != null) {
            values.add(value);
          }
        }
      }
    }

    return new ArrayList<>(values);
  }

  private Map<String, Object> copyObjectNodeOrNull(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode() || !node.isObject()) {
      return null;
    }
    return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
  }

  private String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
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