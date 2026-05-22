package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalDatasetBucketTest {

    private LocalDatasetBucket bucket(String min, String max, Long minIdx, Long maxIdx) {
        return new LocalDatasetBucket(0, "file.csv", 100L, min, max, minIdx, maxIdx);
    }

    // --- containsBarcode ---

    @Test
    void containsBarcode_barcodeInRange_returnsTrue() {
        LocalDatasetBucket b = bucket("100", "200", null, null);
        assertTrue(b.containsBarcode("150"));
    }

    @Test
    void containsBarcode_barcodeAtMin_returnsTrue() {
        LocalDatasetBucket b = bucket("100", "200", null, null);
        assertTrue(b.containsBarcode("100"));
    }

    @Test
    void containsBarcode_barcodeAtMax_returnsTrue() {
        LocalDatasetBucket b = bucket("100", "200", null, null);
        assertTrue(b.containsBarcode("200"));
    }

    @Test
    void containsBarcode_barcodeBelowMin_returnsFalse() {
        LocalDatasetBucket b = bucket("100", "200", null, null);
        assertFalse(b.containsBarcode("99"));
    }

    @Test
    void containsBarcode_barcodeAboveMax_returnsFalse() {
        LocalDatasetBucket b = bucket("100", "200", null, null);
        assertFalse(b.containsBarcode("201"));
    }

    @Test
    void containsBarcode_largeNumericBarcode_handledCorrectly() {
        LocalDatasetBucket b = bucket("3017620422003", "3017620422003", null, null);
        assertTrue(b.containsBarcode("3017620422003"));
    }

    // --- containsProductIndex ---

    @Test
    void containsProductIndex_indexInRange_returnsTrue() {
        LocalDatasetBucket b = bucket("0", "9999", 10L, 20L);
        assertTrue(b.containsProductIndex(15L));
    }

    @Test
    void containsProductIndex_indexAtMin_returnsTrue() {
        LocalDatasetBucket b = bucket("0", "9999", 10L, 20L);
        assertTrue(b.containsProductIndex(10L));
    }

    @Test
    void containsProductIndex_indexAtMax_returnsTrue() {
        LocalDatasetBucket b = bucket("0", "9999", 10L, 20L);
        assertTrue(b.containsProductIndex(20L));
    }

    @Test
    void containsProductIndex_indexBelowMin_returnsFalse() {
        LocalDatasetBucket b = bucket("0", "9999", 10L, 20L);
        assertFalse(b.containsProductIndex(9L));
    }

    @Test
    void containsProductIndex_indexAboveMax_returnsFalse() {
        LocalDatasetBucket b = bucket("0", "9999", 10L, 20L);
        assertFalse(b.containsProductIndex(21L));
    }

    @Test
    void containsProductIndex_nullIndex_returnsFalse() {
        LocalDatasetBucket b = bucket("0", "9999", 10L, 20L);
        assertFalse(b.containsProductIndex(null));
    }

    @Test
    void containsProductIndex_nullMinProductIndex_returnsFalse() {
        LocalDatasetBucket b = bucket("0", "9999", null, 20L);
        assertFalse(b.containsProductIndex(15L));
    }

    @Test
    void containsProductIndex_nullMaxProductIndex_returnsFalse() {
        LocalDatasetBucket b = bucket("0", "9999", 10L, null);
        assertFalse(b.containsProductIndex(15L));
    }
}
