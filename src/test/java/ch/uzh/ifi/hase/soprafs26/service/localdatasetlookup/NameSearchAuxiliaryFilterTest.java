package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NameSearchAuxiliaryFilterTest {

    private NameSearchAuxiliaryFilter filter;

    @BeforeEach
    void setUp() {
        filter = new NameSearchAuxiliaryFilter(new NameSearchTextNormalizer());
    }

    private ProductRow row(String name) {
        return new ProductRow(1L, null, name, null, null, null);
    }

    private ProductRow row(String name, String brand, String quantity) {
        return new ProductRow(1L, null, name, brand, quantity, null);
    }

    @Test
    void apply_emptyAuxiliary_returnsAllProducts() {
        List<ProductRow> rows = List.of(row("Whole Milk"), row("Skim Milk"));
        List<ProductRow> result = filter.apply(rows, List.of(), List.of());
        assertEquals(2, result.size());
    }

    @Test
    void apply_blankAuxiliaryTokens_returnsAllProducts() {
        List<ProductRow> rows = List.of(row("Whole Milk"), row("Orange Juice"));
        List<ProductRow> result = filter.apply(rows, List.of("milk"), List.of("  "));
        assertEquals(2, result.size());
    }

    @Test
    void apply_matchingAuxiliary_keepsMatchingProduct() {
        ProductRow milk = row("Whole Milk", null, "1L");
        ProductRow juice = row("Orange Juice", null, "500ml");
        List<ProductRow> rows = List.of(milk, juice);

        // auxiliary = "1l" -> compact = "1l"
        // milk candidate text (excluding anchor "milk") has "whole" + "1l"
        // juice candidate text has "orange" + "500ml"
        List<ProductRow> result = filter.apply(rows, List.of("milk"), List.of("1l"));
        assertTrue(result.contains(milk));
        assertFalse(result.contains(juice));
    }

    @Test
    void apply_nonMatchingAuxiliary_filtersOutProduct() {
        List<ProductRow> rows = List.of(row("Organic Oat Milk"), row("Whole Milk"));
        List<ProductRow> result = filter.apply(rows, List.of("milk"), List.of("xyz"));
        assertTrue(result.isEmpty());
    }

    @Test
    void apply_brandIncludedInCandidateWhenNotInName() {
        // Brand "Nestle" not in name "Chocolate Milk", so it gets included in candidate text
        ProductRow withBrand = row("Chocolate Milk", "Nestle", null);
        List<ProductRow> rows = List.of(withBrand);

        // auxiliary "nestle" should match because brand is included in candidate
        List<ProductRow> result = filter.apply(rows, List.of("chocolate", "milk"), List.of("nestle"));
        assertEquals(1, result.size());
    }

    @Test
    void apply_brandExcludedWhenAlreadyInName() {
        // Brand "Milk" is part of the name, so it should not be added as separate candidate
        ProductRow row = row("Milk Chocolate", "Milk", null);
        List<ProductRow> rows = List.of(row);

        // "milkchoc" as ordered subsequence of "chocolate" (without anchors) - won't match
        List<ProductRow> result = filter.apply(rows, List.of("chocolate"), List.of("milkchoc"));
        assertTrue(result.isEmpty());
    }

    @Test
    void apply_emptyProductList_returnsEmpty() {
        List<ProductRow> result = filter.apply(List.of(), List.of(), List.of("organic"));
        assertTrue(result.isEmpty());
    }

    @Test
    void apply_productWithNullName_doesNotThrow() {
        ProductRow nullName = new ProductRow(1L, null, null, null, null, null);
        assertDoesNotThrow(() -> filter.apply(List.of(nullName), List.of(), List.of("milk")));
    }

    @Test
    void apply_anchorTokensExcludedFromCandidateText() {
        // anchor "milk" excluded from candidate; auxiliary "whole" must match rest
        ProductRow row = row("Whole Milk");
        List<ProductRow> result = filter.apply(List.of(row), List.of("milk"), List.of("whole"));
        assertEquals(1, result.size());
    }

    @Test
    void apply_multipleAuxiliaryTokensCombined() {
        ProductRow row = row("Organic Oat Milk", null, "500g");
        // auxiliary = ["or", "ga"] -> compact = "orga" -> must be ordered subsequence of candidate
        List<ProductRow> result = filter.apply(List.of(row), List.of("milk"), List.of("or", "ga"));
        assertEquals(1, result.size());
    }
}
