package ch.uzh.ifi.hase.soprafs26.rest.dto;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReceiptDTOTest {

    // ReceiptPantryItemSuggestionDTO getter/setter coverage

    @Test
    void receiptPantryItemSuggestionDTO_gettersAndSetters() {
        ReceiptPantryItemSuggestionDTO dto = new ReceiptPantryItemSuggestionDTO();

        dto.setBarcode("1234567890");
        dto.setName("Test Product");
        dto.setKcalPerPackage(250.0);
        dto.setQuantity(3);
        dto.setPackageQuantity("200g");
        dto.setNutriments(Map.of("energy", 250.0));
        dto.setReadyForBulkAdd(true);

        assertEquals("1234567890", dto.getBarcode());
        assertEquals("Test Product", dto.getName());
        assertEquals(250.0, dto.getKcalPerPackage(), 0.001);
        assertEquals(3, dto.getQuantity());
        assertEquals("200g", dto.getPackageQuantity());
        assertEquals(1, dto.getNutriments().size());
        assertTrue(dto.getReadyForBulkAdd());
    }

    // ReceiptProductCandidateDTO getter/setter coverage

    @Test
    void receiptProductCandidateDTO_gettersAndSetters() {
        ReceiptProductCandidateDTO dto = new ReceiptProductCandidateDTO();

        ProductDTO product = new ProductDTO();
        ReceiptPantryItemSuggestionDTO suggestion = new ReceiptPantryItemSuggestionDTO();
        suggestion.setName("Suggested");

        dto.setProduct(product);
        dto.setScore(0.95);
        dto.setConfidence("HIGH");
        dto.setMatchSource("BARCODE");
        dto.setSuggestedPantryItem(suggestion);

        assertSame(product, dto.getProduct());
        assertEquals(0.95, dto.getScore(), 0.001);
        assertEquals("HIGH", dto.getConfidence());
        assertEquals("BARCODE", dto.getMatchSource());
        assertEquals("Suggested", dto.getSuggestedPantryItem().getName());
    }
}
