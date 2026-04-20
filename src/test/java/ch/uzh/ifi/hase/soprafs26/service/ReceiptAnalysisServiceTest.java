package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.rest.dto.ReceiptAnalysisResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;

class ReceiptAnalysisServiceTest {

  private ReceiptAnalysisService receiptAnalysisService;
  private RestTemplate restTemplate;

  @BeforeEach
  void setUp() {
    receiptAnalysisService = new ReceiptAnalysisService();
    restTemplate = Mockito.mock(RestTemplate.class);

    ReflectionTestUtils.setField(receiptAnalysisService, "restTemplate", restTemplate);
    ReflectionTestUtils.setField(receiptAnalysisService, "endpoint", "https://example.cognitiveservices.azure.com/");
    ReflectionTestUtils.setField(receiptAnalysisService, "apiKey", "test-key");
    ReflectionTestUtils.setField(receiptAnalysisService, "apiVersion", "2024-11-30");
  }

  @Test
  void analyzeReceipt_withValidAzureResponse_mapsStructuredReceipt() {
    MockMultipartFile image = new MockMultipartFile(
        "image",
        "receipt.png",
        MediaType.IMAGE_PNG_VALUE,
        "fake-image-content".getBytes()
    );

    HttpHeaders acceptedHeaders = new HttpHeaders();
    acceptedHeaders.add("Operation-Location",
        "https://example.cognitiveservices.azure.com/documentintelligence/documentModels/prebuilt-receipt/analyzeResults/123?api-version=2024-11-30");

    String resultBody = """
        {
          "status": "succeeded",
          "analyzeResult": {
            "content": "Migros milk 2.40 CHF",
            "documents": [
              {
                "fields": {
                  "MerchantName": {
                    "type": "string",
                    "valueString": "Migros"
                  },
                  "MerchantPhoneNumber": {
                    "type": "phoneNumber",
                    "valuePhoneNumber": "+41 44 123 45 67"
                  },
                  "TransactionDate": {
                    "type": "date",
                    "valueDate": "2026-04-20"
                  },
                  "Total": {
                    "type": "currency",
                    "valueCurrency": {
                      "amount": 12.5,
                      "currencyCode": "CHF",
                      "currencySymbol": "CHF"
                    },
                    "content": "12.50 CHF"
                  },
                  "TotalTax": {
                    "type": "currency",
                    "valueCurrency": {
                      "amount": 0.9,
                      "currencyCode": "CHF",
                      "currencySymbol": "CHF"
                    },
                    "content": "0.90 CHF"
                  },
                  "Items": {
                    "type": "array",
                    "valueArray": [
                      {
                        "type": "object",
                        "valueObject": {
                          "Description": {
                            "type": "string",
                            "valueString": "Milk"
                          },
                          "Quantity": {
                            "type": "number",
                            "valueNumber": 2
                          },
                          "Price": {
                            "type": "currency",
                            "valueCurrency": {
                              "amount": 1.2,
                              "currencyCode": "CHF",
                              "currencySymbol": "CHF"
                            },
                            "content": "1.20 CHF"
                          },
                          "TotalPrice": {
                            "type": "currency",
                            "valueCurrency": {
                              "amount": 2.4,
                              "currencyCode": "CHF",
                              "currencySymbol": "CHF"
                            },
                            "content": "2.40 CHF"
                          }
                        }
                      }
                    ]
                  }
                }
              }
            ]
          }
        }
        """;

    Mockito.when(restTemplate.exchange(contains(":analyze?_overload=analyzeDocument"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity<>("", acceptedHeaders, HttpStatus.ACCEPTED));
    Mockito.when(restTemplate.exchange(contains("/analyzeResults/123"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity<>(resultBody, HttpStatus.OK));

    ReceiptAnalysisResponseDTO response = receiptAnalysisService.analyzeReceipt(image);

    assertEquals("succeeded", response.getStatus());
    assertEquals("Migros", response.getMerchantName());
    assertEquals("+41 44 123 45 67", response.getMerchantPhoneNumber());
    assertEquals("2026-04-20", response.getTransactionDate());
    assertEquals("12.50 CHF", response.getTotal());
    assertEquals("0.90 CHF", response.getTax());
    assertEquals("CHF", response.getCurrencyCode());
    assertEquals(1, response.getItems().size());
    assertEquals("Milk", response.getItems().get(0).getDescription());
    assertEquals("2.40 CHF", response.getItems().get(0).getTotalPrice());
    assertTrue(response.getExtractedFields().containsKey("Items"));
  }

  @Test
  void analyzeReceipt_missingAzureConfiguration_throwsServiceUnavailable() {
    ReflectionTestUtils.setField(receiptAnalysisService, "endpoint", "");
    ReflectionTestUtils.setField(receiptAnalysisService, "apiKey", "");

    MockMultipartFile image = new MockMultipartFile(
        "image",
        "receipt.png",
        MediaType.IMAGE_PNG_VALUE,
        "fake-image-content".getBytes()
    );

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> receiptAnalysisService.analyzeReceipt(image)
    );

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    assertTrue(exception.getReason().contains("Azure Document Intelligence is not configured"));
  }
}
