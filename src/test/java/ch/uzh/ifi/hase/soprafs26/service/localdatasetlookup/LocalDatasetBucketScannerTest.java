package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for LocalDatasetBucketScanner using the real production dataset files
 * that are available on the classpath during testing.
 */
class LocalDatasetBucketScannerTest {

    private LocalDatasetBucketScanner scanner;

    // Bucket 0: barcode range 0..3660119, product_index range 1..42235
    // Row 1: barcode="00000000", product_index=1
    // Row 2: barcode="00000007", product_index=2
    private static final LocalDatasetBucket BUCKET_0 = new LocalDatasetBucket(
            0, "bucket_000.csv", 42235L, "0", "3660119", 1L, 42235L
    );

    @BeforeEach
    void setUp() {
        scanner = new LocalDatasetBucketScanner();
    }

    // --- findRowByBarcode ---

    @Test
    void findRowByBarcode_knownBarcode_returnsRow() {
        Optional<Map<String, String>> result = scanner.findRowByBarcode(BUCKET_0, "00000000");
        assertTrue(result.isPresent());
        assertEquals("00000000", result.get().get("code"));
    }

    @Test
    void findRowByBarcode_unknownBarcode_returnsEmpty() {
        Optional<Map<String, String>> result = scanner.findRowByBarcode(BUCKET_0, "99999999999");
        assertFalse(result.isPresent());
    }

    @Test
    void findRowByBarcode_nullBarcode_throwsException() {
        assertThrows(NullPointerException.class,
                () -> scanner.findRowByBarcode(BUCKET_0, null));
    }

    @Test
    void findRowByBarcode_nullBucket_throwsException() {
        assertThrows(NullPointerException.class,
                () -> scanner.findRowByBarcode(null, "00000000"));
    }

    // --- findRowByProductIndex ---

    @Test
    void findRowByProductIndex_nullIndex_returnsEmpty() {
        Optional<Map<String, String>> result = scanner.findRowByProductIndex(BUCKET_0, null);
        assertFalse(result.isPresent());
    }

    @Test
    void findRowByProductIndex_indexInRange_returnsRow() {
        Optional<Map<String, String>> result = scanner.findRowByProductIndex(BUCKET_0, 1L);
        assertTrue(result.isPresent());
        assertEquals("1", result.get().get("product_index"));
    }

    @Test
    void findRowByProductIndex_indexOutOfRange_returnsEmpty() {
        Optional<Map<String, String>> result = scanner.findRowByProductIndex(BUCKET_0, 999999L);
        assertFalse(result.isPresent());
    }

    // --- findRowsByProductIndices ---

    @Test
    void findRowsByProductIndices_nullCollection_returnsEmpty() {
        Map<Long, Map<String, String>> result = scanner.findRowsByProductIndices(BUCKET_0, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void findRowsByProductIndices_multipleValidIndices_returnsMatchingRows() {
        Map<Long, Map<String, String>> result = scanner.findRowsByProductIndices(BUCKET_0, List.of(1L, 2L));
        assertEquals(2, result.size());
        assertTrue(result.containsKey(1L));
        assertTrue(result.containsKey(2L));
    }

    @Test
    void findRowsByProductIndices_indicesOutOfBucketRange_returnsEmpty() {
        Map<Long, Map<String, String>> result = scanner.findRowsByProductIndices(BUCKET_0, List.of(999999L));
        assertTrue(result.isEmpty());
    }
}
