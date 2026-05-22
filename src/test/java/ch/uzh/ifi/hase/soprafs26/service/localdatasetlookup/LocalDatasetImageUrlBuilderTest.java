package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalDatasetImageUrlBuilderTest {

    private LocalDatasetImageUrlBuilder builder;

    private static final String BASE = "https://images.openfoodfacts.org/images/products";

    @BeforeEach
    void setUp() {
        builder = new LocalDatasetImageUrlBuilder();
    }

    @Test
    void buildBestImageUrl_nullBarcode_returnsNull() {
        assertNull(builder.buildBestImageUrl(null, "{\"en\":\"42\"}", null));
    }

    @Test
    void buildBestImageUrl_nullImages_returnsNull() {
        assertNull(builder.buildBestImageUrl("3017624010701", null, null));
    }

    @Test
    void buildBestImageUrl_emptyImages_returnsNull() {
        assertNull(builder.buildBestImageUrl("3017624010701", "{}", "{}"));
    }

    @Test
    void buildBestImageUrl_validEnLanguage_buildsCorrectUrl() {
        String url = builder.buildBestImageUrl("3017624010701", "{\"en\":\"42\"}", null);
        assertNotNull(url);
        assertEquals(BASE + "/301/762/401/0701/front_en.42.400.jpg", url);
    }

    @Test
    void buildBestImageUrl_deLanguage_usesFrontDePrefix() {
        String url = builder.buildBestImageUrl("3017624010701", "{\"de\":\"7\"}", null);
        assertNotNull(url);
        assertTrue(url.contains("front_de.7.400.jpg"), "Expected front_de prefix, got: " + url);
    }

    @Test
    void buildBestImageUrl_defaultLanguage_usesFrontPrefix() {
        String url = builder.buildBestImageUrl("3017624010701", "{\"default\":\"99\"}", null);
        assertNotNull(url);
        assertTrue(url.contains("front.99.400.jpg"), "Expected front prefix, got: " + url);
    }

    @Test
    void buildBestImageUrl_prefersEnOverDe() {
        String url = builder.buildBestImageUrl("3017624010701", "{\"de\":\"1\",\"en\":\"2\"}", null);
        assertNotNull(url);
        assertTrue(url.contains("front_en.2.400.jpg"), "Should prefer en, got: " + url);
    }

    @Test
    void buildBestImageUrl_fallsBackToImage2WhenImage1Null() {
        String url = builder.buildBestImageUrl("3017624010701", null, "{\"en\":\"55\"}");
        assertNotNull(url);
        assertTrue(url.contains("front_en.55.400.jpg"), "Should use image2, got: " + url);
    }

    @Test
    void buildBestImageUrl_shortBarcodePaddedTo13Digits() {
        // barcode "123" -> padded to "0000000000123"
        String url = builder.buildBestImageUrl("123", "{\"en\":\"1\"}", null);
        assertNotNull(url);
        assertTrue(url.contains("000/000/000/0123"), "Short barcode should be padded, got: " + url);
    }

    @Test
    void buildBestImageUrl_nonNumericBarcode_returnsNull() {
        assertNull(builder.buildBestImageUrl("ABC1234567890", "{\"en\":\"1\"}", null));
    }

    @Test
    void buildBestImageUrl_invalidJson_returnsNull() {
        assertNull(builder.buildBestImageUrl("3017624010701", "not-json", null));
    }

    @Test
    void buildBestImageUrl_arrayJson_returnsNull() {
        assertNull(builder.buildBestImageUrl("3017624010701", "[\"en\"]", null));
    }

    @Test
    void buildFromImage1_sameAsDirectBuild() {
        String direct = builder.buildBestImageUrl("3017624010701", "{\"en\":\"5\"}", null);
        String fromImage1 = builder.buildFromImage1("3017624010701", "{\"en\":\"5\"}");
        assertEquals(direct, fromImage1);
    }

    @Test
    void buildFromImage2_buildsCorrectUrl() {
        String url = builder.buildFromImage2("3017624010701", "{\"fr\":\"8\"}");
        assertNotNull(url);
        assertTrue(url.contains("front_fr.8.400.jpg"), "Expected front_fr prefix, got: " + url);
    }
}
