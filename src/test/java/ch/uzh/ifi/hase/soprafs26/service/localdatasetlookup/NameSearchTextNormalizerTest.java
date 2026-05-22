package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NameSearchTextNormalizerTest {

    private NameSearchTextNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new NameSearchTextNormalizer();
    }

    // normalizeText

    @Test
    void normalizeText_nullReturnsEmpty() {
        assertEquals("", normalizer.normalizeText(null));
    }

    @Test
    void normalizeText_emptyReturnsEmpty() {
        assertEquals("", normalizer.normalizeText(""));
    }

    @Test
    void normalizeText_lowercasesInput() {
        assertEquals("hello world", normalizer.normalizeText("Hello World"));
    }

    @Test
    void normalizeText_stripsAccents() {
        assertEquals("cafe au lait", normalizer.normalizeText("Café au lait"));
    }

    @Test
    void normalizeText_stripsUmlautsAndAccents() {
        assertEquals("uber milch kase", normalizer.normalizeText("Über Milch Käse"));
    }

    @Test
    void normalizeText_collapsesNonAlphanumericToSingleSpace() {
        assertEquals("a b c", normalizer.normalizeText("a---b...c"));
    }

    @Test
    void normalizeText_trimsBothEnds() {
        assertEquals("hello", normalizer.normalizeText("  hello  "));
    }

    @Test
    void normalizeText_mixedSpecialCharsAndLetters() {
        assertEquals("oat flakes 500g", normalizer.normalizeText("Oat Flakes (500g)"));
    }

    @Test
    void normalizeText_digitsPreserved() {
        assertEquals("product 100", normalizer.normalizeText("Product 100"));
    }

    // tokenize

    @Test
    void tokenize_nullReturnsEmpty() {
        assertTrue(normalizer.tokenize(null).isEmpty());
    }

    @Test
    void tokenize_emptyReturnsEmpty() {
        assertTrue(normalizer.tokenize("").isEmpty());
    }

    @Test
    void tokenize_singleCharTokensFiltered() {
        List<String> tokens = normalizer.tokenize("a b cd");
        assertFalse(tokens.contains("a"));
        assertFalse(tokens.contains("b"));
        assertTrue(tokens.contains("cd"));
    }

    @Test
    void tokenize_deduplicatesTokens() {
        List<String> tokens = normalizer.tokenize("milk milk chocolate");
        assertEquals(1, tokens.stream().filter("milk"::equals).count());
    }

    @Test
    void tokenize_splitsByNonAlphanumeric() {
        List<String> tokens = normalizer.tokenize("whole-milk 3.5%");
        assertTrue(tokens.contains("whole"));
        assertTrue(tokens.contains("milk"));
    }

    @Test
    void tokenize_normalSentence() {
        List<String> tokens = normalizer.tokenize("Oat Flakes 500g");
        assertTrue(tokens.contains("oat"));
        assertTrue(tokens.contains("flakes"));
        assertTrue(tokens.contains("500g"));
    }

    // compactText

    @Test
    void compactText_nullReturnsEmpty() {
        assertEquals("", normalizer.compactText(null));
    }

    @Test
    void compactText_removesAllSpaces() {
        assertEquals("oatflakes", normalizer.compactText("Oat Flakes"));
    }

    @Test
    void compactText_stripsSpecialCharsAndSpaces() {
        assertEquals("cafe", normalizer.compactText("Café"));
    }

    @Test
    void compactText_preservesDigits() {
        assertEquals("milk500", normalizer.compactText("milk 500"));
    }
}
