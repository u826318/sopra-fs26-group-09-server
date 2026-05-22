package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NameSearchScorerTest {

    private NameSearchScorer scorer;

    @BeforeEach
    void setUp() {
        NameSearchTextNormalizer normalizer = new NameSearchTextNormalizer();
        scorer = new NameSearchScorer(normalizer);
    }

    private ProductRow product(String name) {
        return new ProductRow(1L, "1234567890123", name, null, null, null);
    }

    private ProductRow product(String name, String brand, String quantity) {
        return new ProductRow(1L, "1234567890123", name, brand, quantity, null);
    }

    private ProductRow productWithSearchText(String name, String searchText) {
        return new ProductRow(1L, "1234567890123", name, null, null, searchText);
    }

    @Test
    void score_exactMatchGivesHighScore() {
        ProductRow row = product("Whole Milk");
        ScoredProduct result = scorer.score(row, List.of("whole", "milk"), List.of("whole", "milk"), List.of());
        assertTrue(result.score() > 300.0, "Exact match should score > 300, got " + result.score());
    }

    @Test
    void score_noMatchGivesLowScore() {
        ProductRow row = product("Orange Juice");
        ScoredProduct result = scorer.score(row, List.of("milk"), List.of("milk"), List.of());
        assertTrue(result.score() < 100.0, "No match should score < 100, got " + result.score());
    }

    @Test
    void score_emptyAnchorTokensGivesNoAnchorBonus() {
        ProductRow row = product("Whole Milk");
        ScoredProduct result = scorer.score(row, List.of("whole"), List.of(), List.of());
        assertNotNull(result);
        assertTrue(result.score() >= 0.0);
    }

    @Test
    void score_allAnchorsPresent_addsAllPresentBonus() {
        ProductRow row = product("Whole Milk Organic");
        ScoredProduct a = scorer.score(row, List.of("whole", "milk"), List.of("whole", "milk"), List.of());
        ScoredProduct b = scorer.score(row, List.of("whole"), List.of("whole"), List.of());
        // With both anchors present the 120 "all present" bonus should make a > b in anchor contribution
        assertTrue(a.score() > b.score());
    }

    @Test
    void score_inOrderAnchorAddsOrderBonus() {
        ProductRow row = product("Whole Milk");
        ScoredProduct inOrder = scorer.score(row, List.of("whole", "milk"), List.of("whole", "milk"), List.of());
        // Out-of-order would need a product where tokens appear reversed
        ProductRow reversed = product("Milk Whole");
        ScoredProduct outOrder = scorer.score(reversed, List.of("whole", "milk"), List.of("whole", "milk"), List.of());
        // Both present, but in-order product gets anchor span bonus
        assertTrue(inOrder.score() > outOrder.score());
    }

    @Test
    void score_auxiliaryTokenBonus_presentTokenIncreasesScore() {
        ProductRow withBrand = product("Nesquik Chocolate Milk", "Nestle", "500ml");
        ProductRow withoutBrand = product("Generic Chocolate Milk", null, "500ml");
        ScoredProduct withAux = scorer.score(withBrand, List.of("chocolate", "milk"), List.of("chocolate", "milk"), List.of("nestle"));
        ScoredProduct withoutAux = scorer.score(withoutBrand, List.of("chocolate", "milk"), List.of("chocolate", "milk"), List.of("nestle"));
        assertTrue(withAux.score() > withoutAux.score(), "Matching auxiliary brand should give higher score");
    }

    @Test
    void score_usesSearchTextFieldWhenSet() {
        ProductRow row = productWithSearchText("Other Name", "whole milk organic");
        ScoredProduct result = scorer.score(row, List.of("whole", "milk"), List.of("whole", "milk"), List.of());
        assertTrue(result.score() > 100.0, "searchText field should be used for scoring, got " + result.score());
    }

    @Test
    void score_returnsProductUnchanged() {
        ProductRow row = product("Test");
        ScoredProduct result = scorer.score(row, List.of("test"), List.of("test"), List.of());
        assertSame(row, result.product());
    }

    @Test
    void score_nonFiniteScoreReplacedByZero() {
        // Edge case: empty product name with empty tokens
        ProductRow row = new ProductRow(1L, null, "", null, null, null);
        ScoredProduct result = scorer.score(row, List.of(), List.of(), List.of());
        assertTrue(Double.isFinite(result.score()));
    }

    @Test
    void score_toDtoMapsFieldsCorrectly() {
        ProductRow row = new ProductRow(42L, "9876543210123", "Organic Milk", "FarmBrand", "1L", null);
        ScoredProduct scored = scorer.score(row, List.of("organic"), List.of("organic"), List.of());
        var dto = scored.toDto();
        assertEquals(42L, dto.getProductIndex());
        assertEquals("9876543210123", dto.getBarcode());
        assertEquals("Organic Milk", dto.getName());
        assertEquals("FarmBrand", dto.getBrand());
        assertEquals("1L", dto.getQuantity());
    }
}
