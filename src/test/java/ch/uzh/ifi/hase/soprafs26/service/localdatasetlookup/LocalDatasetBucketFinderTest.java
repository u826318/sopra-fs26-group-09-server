package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LocalDatasetBucketFinderTest {

    private LocalDatasetBucketFinder finder;

    @BeforeEach
    void setUp() {
        finder = new LocalDatasetBucketFinder();
    }

    private LocalDatasetBucket bucket(int id, String min, String max, Long minIdx, Long maxIdx) {
        return new LocalDatasetBucket(id, "file" + id + ".csv", 100L, min, max, minIdx, maxIdx);
    }

    // --- findBucketForBarcode ---

    @Test
    void findBucketForBarcode_nullBarcode_returnsEmpty() {
        LocalDatasetBucket b = bucket(0, "100", "999", null, null);
        Optional<LocalDatasetBucket> result = finder.findBucketForBarcode(null, List.of(b));
        assertFalse(result.isPresent());
    }

    @Test
    void findBucketForBarcode_nullBuckets_returnsEmpty() {
        Optional<LocalDatasetBucket> result = finder.findBucketForBarcode("123", null);
        assertFalse(result.isPresent());
    }

    @Test
    void findBucketForBarcode_emptyBuckets_returnsEmpty() {
        Optional<LocalDatasetBucket> result = finder.findBucketForBarcode("123", List.of());
        assertFalse(result.isPresent());
    }

    @Test
    void findBucketForBarcode_barcodeInRange_returnsBucket() {
        LocalDatasetBucket bucket1 = bucket(1, "100", "200", null, null);
        LocalDatasetBucket bucket2 = bucket(2, "201", "300", null, null);
        Optional<LocalDatasetBucket> result = finder.findBucketForBarcode("150", List.of(bucket1, bucket2));
        assertTrue(result.isPresent());
        assertEquals(1, result.get().bucketId());
    }

    @Test
    void findBucketForBarcode_barcodeNotInAnyRange_returnsEmpty() {
        LocalDatasetBucket bucket1 = bucket(1, "100", "200", null, null);
        LocalDatasetBucket bucket2 = bucket(2, "201", "300", null, null);
        Optional<LocalDatasetBucket> result = finder.findBucketForBarcode("999", List.of(bucket1, bucket2));
        assertFalse(result.isPresent());
    }

    // --- findBucketForProductIndex ---

    @Test
    void findBucketForProductIndex_nullProductIndex_returnsEmpty() {
        LocalDatasetBucket b = bucket(0, "0", "9999", 0L, 100L);
        Optional<LocalDatasetBucket> result = finder.findBucketForProductIndex(null, List.of(b));
        assertFalse(result.isPresent());
    }

    @Test
    void findBucketForProductIndex_nullList_returnsEmpty() {
        Optional<LocalDatasetBucket> result = finder.findBucketForProductIndex(5L, null);
        assertFalse(result.isPresent());
    }

    @Test
    void findBucketForProductIndex_indexInRange_returnsBucket() {
        LocalDatasetBucket b = bucket(3, "0", "9999", 10L, 50L);
        Optional<LocalDatasetBucket> result = finder.findBucketForProductIndex(25L, List.of(b));
        assertTrue(result.isPresent());
        assertEquals(3, result.get().bucketId());
    }

    @Test
    void findBucketForProductIndex_indexOutOfRange_returnsEmpty() {
        LocalDatasetBucket b = bucket(3, "0", "9999", 10L, 50L);
        Optional<LocalDatasetBucket> result = finder.findBucketForProductIndex(99L, List.of(b));
        assertFalse(result.isPresent());
    }
}
