package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.rest.dto.ReceiptAnalysisResponseDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ReceiptLineItemDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReceiptAnalysisService {

  private static final Logger log = LoggerFactory.getLogger(ReceiptAnalysisService.class);
  private static final String MODEL_ID = "prebuilt-receipt";
  private static final int MAX_POLL_ATTEMPTS = 20;
  private static final long POLL_INTERVAL_MS = 750L;

  @Value("${azure.document-intelligence.endpoint:}")
  private String endpoint = "";

  @Value("${azure.document-intelligence.api-key:}")
  private String apiKey = "";

  @Value("${azure.document-intelligence.api-version:2024-11-30}")
  private String apiVersion = "2024-11-30";

  private final RestTemplate restTemplate = new RestTemplate();
  private final ObjectMapper objectMapper = new ObjectMapper();

  public ReceiptAnalysisResponseDTO analyzeReceipt(MultipartFile image) {
    if (image == null || image.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receipt image must not be empty.");
    }

    if (blankToNull(endpoint) == null || blankToNull(apiKey) == null) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Azure Document Intelligence is not configured. Set AZURE_DOCUMENT_INTELLIGENCE_ENDPOINT and AZURE_DOCUMENT_INTELLIGENCE_API_KEY."
      );
    }

    try {
      String operationLocation = submitAnalyzeRequest(image);
      JsonNode resultNode = pollAnalyzeResult(operationLocation);
      return mapReceiptAnalysis(resultNode);
    }
    catch (ResponseStatusException exception) {
      throw exception;
    }
    catch (IOException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read uploaded receipt image.", exception);
    }
    catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Receipt analysis was interrupted.", exception);
    }
    catch (RestClientException exception) {
      log.error("Azure Document Intelligence request failed", exception);
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Azure Document Intelligence request failed.", exception);
    }
  }

  private String submitAnalyzeRequest(MultipartFile image) throws IOException {
    HttpHeaders headers = createAzureHeaders(resolveMediaType(image.getContentType()));
    HttpEntity<byte[]> requestEntity = new HttpEntity<>(image.getBytes(), headers);

    ResponseEntity<String> response = restTemplate.exchange(
        buildAnalyzeUrl(),
        HttpMethod.POST,
        requestEntity,
        String.class
    );

    if (!response.getStatusCode().is2xxSuccessful() && response.getStatusCode() != HttpStatus.ACCEPTED) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY,
          "Azure Document Intelligence analyze request failed: " + response.getStatusCode()
      );
    }

    String operationLocation = response.getHeaders().getFirst("Operation-Location");
    if (blankToNull(operationLocation) == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY,
          "Azure Document Intelligence did not return an operation location."
      );
    }

    return operationLocation;
  }

  private JsonNode pollAnalyzeResult(String operationLocation) throws InterruptedException {
    HttpEntity<Void> requestEntity = new HttpEntity<>(createAzureHeaders(null));

    for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
      ResponseEntity<String> response = restTemplate.exchange(
          operationLocation,
          HttpMethod.GET,
          requestEntity,
          String.class
      );

      if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            "Azure Document Intelligence result polling failed: " + response.getStatusCode()
        );
      }

      try {
        JsonNode resultNode = objectMapper.readTree(response.getBody());
        String status = blankToNull(resultNode.path("status").asText(null));
        if (status == null) {
          throw new ResponseStatusException(
              HttpStatus.BAD_GATEWAY,
              "Azure Document Intelligence returned a malformed result payload."
          );
        }

        String normalizedStatus = status.toLowerCase(Locale.ROOT);
        if ("succeeded".equals(normalizedStatus)) {
          return resultNode;
        }
        if ("failed".equals(normalizedStatus) || "canceled".equals(normalizedStatus)) {
          throw new ResponseStatusException(
              HttpStatus.BAD_GATEWAY,
              "Azure Document Intelligence analysis ended with status: " + status
          );
        }
      }
      catch (IOException exception) {
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            "Failed to parse Azure Document Intelligence result.",
            exception
        );
      }

      Thread.sleep(POLL_INTERVAL_MS);
    }

    throw new ResponseStatusException(
        HttpStatus.GATEWAY_TIMEOUT,
        "Azure Document Intelligence analysis timed out while polling for the receipt result."
    );
  }

  private ReceiptAnalysisResponseDTO mapReceiptAnalysis(JsonNode resultNode) {
    ReceiptAnalysisResponseDTO response = new ReceiptAnalysisResponseDTO();
    response.setStatus(blankToNull(resultNode.path("status").asText(null)));
    response.setRawText(blankToNull(resultNode.path("analyzeResult").path("content").asText(null)));
    response.setRawResult(objectMapper.convertValue(resultNode, new TypeReference<Map<String, Object>>() {}));

    JsonNode documentsNode = resultNode.path("analyzeResult").path("documents");
    if (!documentsNode.isArray() || documentsNode.isEmpty()) {
      response.setExtractedFields(Map.of());
      response.setItems(List.of());
      return response;
    }

    JsonNode firstDocument = documentsNode.get(0);
    JsonNode fieldsNode = firstDocument.path("fields");

    LinkedHashMap<String, Object> extractedFields = new LinkedHashMap<>();
    if (fieldsNode.isObject()) {
      fieldsNode.fields().forEachRemaining(entry -> extractedFields.put(entry.getKey(), extractFieldValue(entry.getValue())));
    }

    response.setExtractedFields(extractedFields);
    response.setMerchantName(asDisplayString(extractedFields.get("MerchantName")));
    response.setMerchantPhoneNumber(asDisplayString(extractedFields.get("MerchantPhoneNumber")));
    response.setMerchantAddress(asDisplayString(extractedFields.get("MerchantAddress")));
    response.setTransactionDate(asDisplayString(extractedFields.get("TransactionDate")));
    response.setTransactionTime(asDisplayString(extractedFields.get("TransactionTime")));
    response.setSubtotal(asDisplayString(extractedFields.get("Subtotal")));
    response.setTax(asDisplayString(extractedFields.get("TotalTax")));
    response.setTotal(asDisplayString(extractedFields.get("Total")));
    response.setTip(asDisplayString(extractedFields.get("Tip")));
    response.setReceiptType(asDisplayString(extractedFields.get("ReceiptType")));
    response.setCurrencyCode(resolveCurrencyCode(extractedFields));
    response.setCountryRegion(asDisplayString(extractedFields.get("CountryRegion")));
    response.setItems(mapItems(extractedFields.get("Items")));

    return response;
  }

  private List<ReceiptLineItemDTO> mapItems(Object itemsValue) {
    if (!(itemsValue instanceof List<?> itemList)) {
      return List.of();
    }

    List<ReceiptLineItemDTO> items = new ArrayList<>();
    for (Object itemValue : itemList) {
      if (!(itemValue instanceof Map<?, ?> rawItemMap)) {
        continue;
      }

      ReceiptLineItemDTO item = new ReceiptLineItemDTO();
      item.setDescription(asDisplayString(rawItemMap.get("Description")));
      item.setQuantity(asDisplayString(rawItemMap.get("Quantity")));
      item.setPrice(asDisplayString(rawItemMap.get("Price")));
      item.setTotalPrice(asDisplayString(rawItemMap.get("TotalPrice")));
      item.setProductCode(asDisplayString(rawItemMap.get("ProductCode")));
      item.setRawItem(castStringObjectMap(rawItemMap));
      items.add(item);
    }

    return items;
  }

  private Map<String, Object> castStringObjectMap(Map<?, ?> rawItemMap) {
    LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
    rawItemMap.forEach((key, value) -> copy.put(String.valueOf(key), value));
    return copy;
  }

  private Object extractFieldValue(JsonNode fieldNode) {
    if (fieldNode == null || fieldNode.isNull() || fieldNode.isMissingNode()) {
      return null;
    }

    JsonNode valueObject = fieldNode.get("valueObject");
    if (valueObject != null && valueObject.isObject()) {
      LinkedHashMap<String, Object> values = new LinkedHashMap<>();
      valueObject.fields().forEachRemaining(entry -> values.put(entry.getKey(), extractFieldValue(entry.getValue())));
      return values;
    }

    JsonNode valueArray = fieldNode.get("valueArray");
    if (valueArray != null && valueArray.isArray()) {
      List<Object> values = new ArrayList<>();
      for (JsonNode childNode : valueArray) {
        values.add(extractFieldValue(childNode));
      }
      return values;
    }

    JsonNode valueCurrency = fieldNode.get("valueCurrency");
    if (valueCurrency != null && valueCurrency.isObject()) {
      return objectMapper.convertValue(valueCurrency, new TypeReference<Map<String, Object>>() {});
    }

    String[] textualProperties = {
        "valueString",
        "valueDate",
        "valueTime",
        "valueDateTime",
        "valuePhoneNumber",
        "valueCountryRegion",
        "valueSelectionMark"
    };
    for (String property : textualProperties) {
      String value = blankToNull(fieldNode.path(property).asText(null));
      if (value != null) {
        return value;
      }
    }

    if (fieldNode.has("valueInteger")) {
      return fieldNode.path("valueInteger").asLong();
    }
    if (fieldNode.has("valueNumber")) {
      return fieldNode.path("valueNumber").asDouble();
    }
    if (fieldNode.has("valueBoolean")) {
      return fieldNode.path("valueBoolean").asBoolean();
    }

    String content = blankToNull(fieldNode.path("content").asText(null));
    if (content != null) {
      return content;
    }

    return objectMapper.convertValue(fieldNode, new TypeReference<Map<String, Object>>() {});
  }

  private String resolveCurrencyCode(Map<String, Object> extractedFields) {
    String[] currencyCandidates = {"Total", "Subtotal", "TotalTax", "Tip"};
    for (String candidateKey : currencyCandidates) {
      Object candidate = extractedFields.get(candidateKey);
      if (candidate instanceof Map<?, ?> currencyMap) {
        Object rawCode = currencyMap.get("currencyCode");
        String code = blankToNull(rawCode == null ? null : String.valueOf(rawCode));
        if (code != null) {
          return code;
        }
      }
    }
    return null;
  }

  private String asDisplayString(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return blankToNull(stringValue);
    }
    if (value instanceof Number || value instanceof Boolean) {
      return String.valueOf(value);
    }
    if (value instanceof Map<?, ?> rawMap) {
      Object content = rawMap.get("content");
      if (content != null) {
        String contentText = blankToNull(String.valueOf(content));
        if (contentText != null) {
          return contentText;
        }
      }
      Object amount = rawMap.get("amount");
      if (amount != null) {
        String amountText = String.valueOf(amount);
        Object currencyCode = rawMap.get("currencyCode");
        if (currencyCode != null && blankToNull(String.valueOf(currencyCode)) != null) {
          return amountText + " " + String.valueOf(currencyCode);
        }
        Object currencySymbol = rawMap.get("currencySymbol");
        if (currencySymbol != null && blankToNull(String.valueOf(currencySymbol)) != null) {
          return String.valueOf(currencySymbol) + amountText;
        }
        return amountText;
      }
      return objectToCompactJson(rawMap);
    }
    if (value instanceof List<?> rawList) {
      return objectToCompactJson(rawList);
    }
    return blankToNull(String.valueOf(value));
  }

  private String objectToCompactJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    }
    catch (Exception exception) {
      return String.valueOf(value);
    }
  }

  private HttpHeaders createAzureHeaders(MediaType contentType) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Ocp-Apim-Subscription-Key", apiKey);
    if (contentType != null) {
      headers.setContentType(contentType);
    }
    return headers;
  }

  private MediaType resolveMediaType(String contentType) {
    String sanitized = blankToNull(contentType);
    if (sanitized == null) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
    try {
      return MediaType.parseMediaType(sanitized);
    }
    catch (IllegalArgumentException exception) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
  }

  private String buildAnalyzeUrl() {
    return normalizeEndpoint(endpoint)
        + "/documentintelligence/documentModels/"
        + MODEL_ID
        + ":analyze?_overload=analyzeDocument&api-version="
        + apiVersion;
  }

  private String normalizeEndpoint(String rawEndpoint) {
    String sanitized = blankToNull(rawEndpoint);
    if (sanitized == null) {
      return "";
    }
    while (sanitized.endsWith("/")) {
      sanitized = sanitized.substring(0, sanitized.length() - 1);
    }
    return sanitized;
  }

  private String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
