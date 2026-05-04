package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.rest.dto.ProductDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class OpenFoodFactsService {

  private static final Logger log = LoggerFactory.getLogger(OpenFoodFactsService.class);

  private static final String OFF_BASE = "https://world.openfoodfacts.org";
  private static final String USER_AGENT = "sopra-fs26-group-09-virtual-pantry/0.1 (OpenFoodFacts portal)";
  private static final int MAX_LIMIT = 12;
  private static final String OFF_PRODUCT_FIELDS = String.join(",",
      "code",
      "product_name",
      "abbreviated_product_name",
      "brands",
      "quantity",
      "serving_size",
      "image_front_url",
      "image_url",
      "url",
      "nutrition_grades",
      "nutriscore_data",
      "nutriments",
      "stores",
      "stores_tags",
      "purchase_places",
      "purchase_places_tags"
  );

  private final RestTemplate restTemplate = new RestTemplate();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final LocalProductDatasetService localProductDatasetService;

  /**
   * Test-friendly constructor kept for existing unit tests that instantiate the
   * service directly with new OpenFoodFactsService(). Spring uses the annotated
   * constructor below so the managed local fallback service is injected.
   */
  public OpenFoodFactsService() {
    this(new LocalProductDatasetService());
  }

  
  @Autowired
  public OpenFoodFactsService(LocalProductDatasetService localProductDatasetService) {
    this.localProductDatasetService = localProductDatasetService;
  }

  public ProductDTO lookupByBarcode(String barcode) {
    String sanitizedBarcode = blankToNull(barcode);
    if (sanitizedBarcode == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "barcode must not be empty");
    }

    debug("[OFF_LOOKUP] lookup requested. barcode='{}'", sanitizedBarcode);

    try {
      ProductDTO offProduct = lookupByBarcodeFromOpenFoodFacts(sanitizedBarcode);
      debug(
          "[OFF_LOOKUP] OpenFoodFacts HIT. barcode='{}', productName='{}', brand='{}'",
          sanitizedBarcode,
          offProduct.getName(),
          offProduct.getBrand()
      );
      return offProduct;
    }
    catch (ResponseStatusException offException) {
      debug(
          "[OFF_LOOKUP] OpenFoodFacts failed. barcode='{}', status={}, reason='{}'. Now trying local CSV fallback.",
          sanitizedBarcode,
          offException.getStatusCode(),
          offException.getReason()
      );

      return localProductDatasetService.lookupByBarcode(sanitizedBarcode)
          .map(localProduct -> {
            debug(
                "[OFF_LOOKUP] local CSV fallback HIT. barcode='{}', productName='{}', brand='{}'",
                sanitizedBarcode,
                localProduct.getName(),
                localProduct.getBrand()
            );
            return localProduct;
          })
          .orElseThrow(() -> {
            debug(
                "[OFF_LOOKUP] local CSV fallback MISS after OpenFoodFacts failure. barcode='{}', originalOffStatus={}, originalOffReason='{}', localDiagnostics='{}'",
                sanitizedBarcode,
                offException.getStatusCode(),
                offException.getReason(),
                localProductDatasetService.getLastDiagnostics()
            );
            return buildCombinedLookupFailure(sanitizedBarcode, offException);
          });
    }
  }

  private ProductDTO lookupByBarcodeFromOpenFoodFacts(String sanitizedBarcode) {
    String url = OFF_BASE
        + "/api/v2/product/"
        + urlEncode(sanitizedBarcode)
        + "?fields="
        + urlEncode(OFF_PRODUCT_FIELDS);
    debug("[OFF_LOOKUP] calling OpenFoodFacts. barcode='{}', url='{}'", sanitizedBarcode, url);
    String body = getWithUserAgent(url);
    debug("[OFF_LOOKUP] OpenFoodFacts HTTP body received. barcode='{}', bodyLength={}", sanitizedBarcode, body.length());

    try {
      JsonNode root = objectMapper.readTree(body);
      int status = root.path("status").asInt(0);
      String statusVerbose = root.path("status_verbose").asText(null);
      debug(
          "[OFF_LOOKUP] OpenFoodFacts JSON parsed. barcode='{}', offStatus={}, statusVerbose='{}', hasProductNode={}",
          sanitizedBarcode,
          status,
          statusVerbose,
          root.has("product")
      );

      if (status != 1) {
        throw new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "OpenFoodFacts: product not found for barcode " + sanitizedBarcode + " (status_verbose=" + statusVerbose + ")"
        );
      }

      JsonNode product = root.path("product");
      return mapProduct(product, sanitizedBarcode);
    }
    catch (ResponseStatusException e) {
      throw e;
    }
    catch (Exception e) {
      debug("Failed to parse OpenFoodFacts response", e);
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY,
          "Failed to parse OpenFoodFacts response",
          e
      );
    }
  }

  private ResponseStatusException buildCombinedLookupFailure(String barcode, ResponseStatusException offException) {
    String localDiagnostics = localProductDatasetService.getLastDiagnostics();

    if (offException.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
      return new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "Product not found in OpenFoodFacts or local dataset for barcode " + barcode + ". Local diagnostics: " + localDiagnostics,
          offException
      );
    }

    return new ResponseStatusException(
        HttpStatus.BAD_GATEWAY,
        "OpenFoodFacts failed and local dataset had no fallback match for barcode " + barcode + ". Local diagnostics: " + localDiagnostics,
        offException
    );
  }

  public List<ProductDTO> search(String query, int limit) {
    String sanitizedQuery = blankToNull(query);
    if (sanitizedQuery == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "q must not be empty");
    }

    int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
    String url = OFF_BASE + "/cgi/search.pl?search_terms=" + urlEncode(sanitizedQuery)
        + "&search_simple=1"
        + "&action=process"
        + "&json=1"
        + "&page_size=" + safeLimit
        + "&fields=" + urlEncode(OFF_PRODUCT_FIELDS);

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
            debug("Falling back to partial search hit for barcode {}: {}", barcode, ex.getReason());
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
      debug("Failed to parse OpenFoodFacts search response", e);
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
    dto.setLocalFallback(false);
    dto.setDataSource("openfoodfacts");
    dto.setCaloriesPerPackage(estimateCaloriesPerPackage(dto.getQuantity(), dto.getNutriments()));

    return dto;
  }

  private Double estimateCaloriesPerPackage(String quantityText, Map<String, Object> nutriments) {
    if (nutriments == null) {
      return null;
    }

    QuantityInfo quantityInfo = parseQuantity(quantityText);
    if (quantityInfo != null) {
      Double kcal100g = parseDoubleFromMap(nutriments, "energy-kcal_100g", "energy_kcal_100g");
      if (kcal100g != null && quantityInfo.basis.equals("100g")) {
        return roundTwoDecimals((kcal100g * quantityInfo.amount) / 100.0);
      }

      Double kcal100ml = parseDoubleFromMap(nutriments, "energy-kcal_100ml", "energy_kcal_100ml");
      if (kcal100ml != null && quantityInfo.basis.equals("100ml")) {
        return roundTwoDecimals((kcal100ml * quantityInfo.amount) / 100.0);
      }
    }

    Double serving = parseDoubleFromMap(nutriments, "energy-kcal_serving", "energy_kcal_serving");
    if (serving != null) {
      return roundTwoDecimals(serving);
    }

    Double fallback100g = parseDoubleFromMap(nutriments, "energy-kcal_100g", "energy_kcal_100g");
    if (fallback100g != null) {
      return roundTwoDecimals(fallback100g);
    }

    Double fallback100ml = parseDoubleFromMap(nutriments, "energy-kcal_100ml", "energy_kcal_100ml");
    return fallback100ml == null ? null : roundTwoDecimals(fallback100ml);
  }

  private QuantityInfo parseQuantity(String quantityText) {
    String text = blankToNull(quantityText);
    if (text == null) {
      return null;
    }

    String normalizedText = text.toLowerCase(Locale.ROOT).replace(',', '.').trim();

    MultipliedQuantity multipliedQuantity = parseMultipliedQuantity(normalizedText);
    if (multipliedQuantity != null) {
      return toQuantityInfo(multipliedQuantity.totalAmount, multipliedQuantity.unit);
    }

    QuantityToken quantityToken = findQuantityToken(normalizedText, 0);
    return quantityToken == null ? null : toQuantityInfo(quantityToken.amount, quantityToken.unit);
  }

  private MultipliedQuantity parseMultipliedQuantity(String text) {
    int index = 0;
    while (index < text.length()) {
      NumberToken count = findNumberToken(text, index);
      if (count == null) {
        return null;
      }

      int cursor = skipWhitespace(text, count.endIndex);
      if (cursor < text.length() && (text.charAt(cursor) == 'x' || text.charAt(cursor) == '×')) {
        QuantityToken quantityToken = findQuantityToken(text, skipWhitespace(text, cursor + 1));
        if (quantityToken != null) {
          return new MultipliedQuantity(count.value * quantityToken.amount, quantityToken.unit);
        }
      }

      index = Math.max(count.endIndex, index + 1);
    }
    return null;
  }

  private QuantityToken findQuantityToken(String text, int startIndex) {
    int index = Math.max(0, startIndex);
    while (index < text.length()) {
      NumberToken number = findNumberToken(text, index);
      if (number == null) {
        return null;
      }

      int unitStart = skipWhitespace(text, number.endIndex);
      UnitToken unit = readUnitToken(text, unitStart);
      if (unit != null) {
        return new QuantityToken(number.value, unit.value);
      }

      index = Math.max(number.endIndex, index + 1);
    }
    return null;
  }

  private NumberToken findNumberToken(String text, int startIndex) {
    int index = Math.max(0, startIndex);
    while (index < text.length() && !Character.isDigit(text.charAt(index))) {
      index++;
    }
    if (index >= text.length()) {
      return null;
    }

    int cursor = index;
    boolean hasDecimalSeparator = false;
    while (cursor < text.length()) {
      char current = text.charAt(cursor);
      if (Character.isDigit(current)) {
        cursor++;
        continue;
      }
      if (current == '.' && !hasDecimalSeparator) {
        hasDecimalSeparator = true;
        cursor++;
        continue;
      }
      break;
    }

    Double value = parseDoubleOrNull(text.substring(index, cursor));
    return value == null ? null : new NumberToken(value, cursor);
  }

  private UnitToken readUnitToken(String text, int startIndex) {
    String[] units = {"kg", "mg", "ml", "cl", "g", "l"};
    for (String unit : units) {
      int endIndex = startIndex + unit.length();
      if (endIndex <= text.length()
          && text.startsWith(unit, startIndex)
          && isUnitBoundary(text, endIndex)) {
        return new UnitToken(unit, endIndex);
      }
    }
    return null;
  }

  private boolean isUnitBoundary(String text, int index) {
    return index >= text.length() || !Character.isLetter(text.charAt(index));
  }

  private int skipWhitespace(String text, int startIndex) {
    int index = startIndex;
    while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
      index++;
    }
    return index;
  }

  private QuantityInfo toQuantityInfo(Double amount, String unit) {
    if (amount == null || unit == null) {
      return null;
    }

    switch (unit) {
      case "kg":
        return new QuantityInfo(amount * 1000.0, "100g");
      case "g":
        return new QuantityInfo(amount, "100g");
      case "mg":
        return new QuantityInfo(amount / 1000.0, "100g");
      case "l":
        return new QuantityInfo(amount * 1000.0, "100ml");
      case "cl":
        return new QuantityInfo(amount * 10.0, "100ml");
      case "ml":
        return new QuantityInfo(amount, "100ml");
      default:
        return null;
    }
  }

  private Double parseDoubleFromMap(Map<String, Object> map, String... keys) {
    for (String key : keys) {
      Object value = map.get(key);
      Double parsed = parseDoubleOrNull(value);
      if (parsed != null) {
        return parsed;
      }
    }

    return null;
  }

  private Double parseDoubleOrNull(Object value) {
    if (value == null) {
      return null;
    }

    if (value instanceof Number number) {
      return number.doubleValue();
    }

    String text = blankToNull(String.valueOf(value));
    if (text == null) {
      return null;
    }

    try {
      return Double.parseDouble(text.replace(',', '.'));
    }
    catch (NumberFormatException ignored) {
      return null;
    }
  }

  private Double roundTwoDecimals(Double value) {
    if (value == null) {
      return null;
    }

    return Math.round(value * 100.0) / 100.0;
  }

  private static class NumberToken {
    private final Double value;
    private final int endIndex;

    private NumberToken(Double value, int endIndex) {
      this.value = value;
      this.endIndex = endIndex;
    }
  }

  private static class UnitToken {
    private final String value;
    private final int endIndex;

    private UnitToken(String value, int endIndex) {
      this.value = value;
      this.endIndex = endIndex;
    }
  }

  private static class QuantityToken {
    private final Double amount;
    private final String unit;

    private QuantityToken(Double amount, String unit) {
      this.amount = amount;
      this.unit = unit;
    }
  }

  private static class MultipliedQuantity {
    private final Double totalAmount;
    private final String unit;

    private MultipliedQuantity(Double totalAmount, String unit) {
      this.totalAmount = totalAmount;
      this.unit = unit;
    }
  }

  private static class QuantityInfo {
    private final Double amount;
    private final String basis;

    private QuantityInfo(Double amount, String basis) {
      this.amount = amount;
      this.basis = basis;
    }
  }

  private String getWithUserAgent(String url) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
      HttpEntity<Void> entity = new HttpEntity<>(headers);
      ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
      debug(
          "[OFF_LOOKUP] OpenFoodFacts HTTP response. url='{}', httpStatus={}, hasBody={}",
          url,
          response.getStatusCode(),
          response.getBody() != null
      );

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
      debug("[OFF_LOOKUP] OpenFoodFacts request failed before local fallback can start. url='{}'", url, e);
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

  private static void debug(String template, Object... args) {
    Object[] safeArgs = sanitizeLogArgs(args);
    log.warn(withoutPlaceholders(template));
    System.err.println(formatForFallbackConsole(template, safeArgs));

    Throwable throwable = trailingThrowable(safeArgs);
    if (throwable != null) {
      System.err.println(throwable.getClass().getSimpleName() + ": " + sanitizeLogValue(throwable.getMessage()));
    }
  }

  private static Object[] sanitizeLogArgs(Object[] args) {
    if (args == null || args.length == 0) {
      return args;
    }
    Object[] sanitized = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      Object arg = args[i];
      sanitized[i] = arg instanceof String value ? sanitizeLogValue(value) : arg;
    }
    return sanitized;
  }

  private static String sanitizeLogValue(String value) {
    return value == null ? null : value.replace('\n', '_').replace('\r', '_');
  }

  private static String withoutPlaceholders(String template) {
    return template == null ? "" : template.replace("{}", "<value>");
  }

  private static String formatForFallbackConsole(String template, Object... args) {
    StringBuilder result = new StringBuilder();
    result.append(java.time.LocalDateTime.now()).append(" ");

    int argLimit = args == null ? 0 : args.length;
    if (argLimit > 0 && args[argLimit - 1] instanceof Throwable) {
      argLimit--;
    }

    int argIndex = 0;
    for (int i = 0; i < template.length(); i++) {
      char current = template.charAt(i);
      if (current == '{' && i + 1 < template.length() && template.charAt(i + 1) == '}' && argIndex < argLimit) {
        result.append(String.valueOf(args[argIndex++]));
        i++;
      }
      else {
        result.append(current);
      }
    }

    if (argIndex < argLimit) {
      result.append(" | extraArgs=");
      for (int i = argIndex; i < argLimit; i++) {
        if (i > argIndex) {
          result.append(", ");
        }
        result.append(String.valueOf(args[i]));
      }
    }

    return result.toString();
  }

  private static Throwable trailingThrowable(Object... args) {
    if (args == null || args.length == 0) {
      return null;
    }

    Object lastArg = args[args.length - 1];
    return lastArg instanceof Throwable throwable ? throwable : null;
  }

}
