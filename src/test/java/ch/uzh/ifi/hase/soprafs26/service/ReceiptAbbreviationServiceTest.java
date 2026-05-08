package ch.uzh.ifi.hase.soprafs26.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReceiptAbbreviationServiceTest {

  private ReceiptAbbreviationService receiptAbbreviationService;

  @BeforeEach
  void setUp() {
    receiptAbbreviationService = new ReceiptAbbreviationService();
  }

  @Test
  void normalize_expandsCommonFamilyGroceryAbbreviations() {
    assertEquals("large egg", receiptAbbreviationService.normalize("LG EGGS 12 CT"));
    assertEquals("whole wheat bread", receiptAbbreviationService.normalize("WHL WHT BRD"));
    assertEquals("boneless skinless chicken", receiptAbbreviationService.normalize("BNLS SKNLS CHKN 1.5 LB"));
    assertEquals("greek yogurt vanilla", receiptAbbreviationService.normalize("GRK YOG VAN 32 OZ"));
  }

  @Test
  void normalize_expandsReceiptSpecificGroceryAbbreviations() {
    assertEquals("banana organic", receiptAbbreviationService.normalize("BANANA BIO"));
    assertEquals("milk", receiptAbbreviationService.normalize("MLK 1L"));
    assertEquals("egg medium", receiptAbbreviationService.normalize("EGGS M 10PCS"));
    assertEquals("chicken breast", receiptAbbreviationService.normalize("CHK BRST (/kg)"));
    assertEquals("pasta spaghetti", receiptAbbreviationService.normalize("PSTA SPGH 500G"));
    assertEquals("tomato sauce", receiptAbbreviationService.normalize("TMTO SAUCE 500G"));
    assertEquals("yogurt strawberry", receiptAbbreviationService.normalize("YOG STRW 150G"));
    assertEquals("apple juice", receiptAbbreviationService.normalize("APL JUICE 1L"));
    assertEquals("cheese cheddar", receiptAbbreviationService.normalize("CHS CHDR 200G"));
    assertEquals("coffee blend", receiptAbbreviationService.normalize("COFFEE BLND 250G"));
    assertEquals("olive oil", receiptAbbreviationService.normalize("OLIV OIL 500ML"));
  }

  @Test
  void normalize_filtersReceiptNoiseAndPackageTokens() {
    assertEquals("organic apple", receiptAbbreviationService.normalize("ORG APPL SALE 2 LB"));
    assertEquals("tomato sauce", receiptAbbreviationService.normalize("TOM SAUCE PKG TAX"));
  }

  @Test
  void tokenize_handlesMixedPunctuationAndCase() {
    assertEquals(List.of("whole", "wheat", "bread"), receiptAbbreviationService.tokenize("Whole-Wheat Bread"));
  }

  @Test
  void isLowValueToken_detectsNumericUnitsAndReceiptNoise() {
    assertTrue(receiptAbbreviationService.isLowValueToken("12"));
    assertTrue(receiptAbbreviationService.isLowValueToken("ct"));
    assertTrue(receiptAbbreviationService.isLowValueToken("subtotal"));
  }
}
