package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LocalDatasetLookupServiceTest {

    private LocalDatasetManifestLoader mockManifestLoader;
    private LocalDatasetBucketFinder mockBucketFinder;
    private LocalDatasetBucketScanner mockBucketScanner;
    private LocalDatasetLookupService service;

    private LocalDatasetBucket sampleBucket;

    @BeforeEach
    void setUp() {
        mockManifestLoader = mock(LocalDatasetManifestLoader.class);
        mockBucketFinder = mock(LocalDatasetBucketFinder.class);
        mockBucketScanner = mock(LocalDatasetBucketScanner.class);
        service = new LocalDatasetLookupService(mockManifestLoader, mockBucketFinder, mockBucketScanner);

        sampleBucket = new LocalDatasetBucket(0, "test.csv", 100L, "100", "999", 1L, 100L);
        when(mockManifestLoader.loadBuckets()).thenReturn(List.of(sampleBucket));
    }

    // --- findRawRowByBarcode ---

    @Test
    void findRawRowByBarcode_nullBarcode_returnsEmpty() {
        Optional<Map<String, String>> result = service.findRawRowByBarcode(null);
        assertFalse(result.isPresent());
        verify(mockManifestLoader, never()).loadBuckets();
    }

    @Test
    void findRawRowByBarcode_blankBarcode_returnsEmpty() {
        Optional<Map<String, String>> result = service.findRawRowByBarcode("   ");
        assertFalse(result.isPresent());
        verify(mockManifestLoader, never()).loadBuckets();
    }

    @Test
    void findRawRowByBarcode_bucketNotFound_returnsEmpty() {
        when(mockBucketFinder.findBucketForBarcode(eq("123"), any())).thenReturn(Optional.empty());

        Optional<Map<String, String>> result = service.findRawRowByBarcode("123");

        assertFalse(result.isPresent());
        verify(mockBucketScanner, never()).findRowByBarcode(any(), any());
    }

    @Test
    void findRawRowByBarcode_bucketFound_delegatesToScanner() {
        Map<String, String> expectedRow = Map.of("code", "123", "product_name", "Test");
        when(mockBucketFinder.findBucketForBarcode(eq("123"), any())).thenReturn(Optional.of(sampleBucket));
        when(mockBucketScanner.findRowByBarcode(sampleBucket, "123")).thenReturn(Optional.of(expectedRow));

        Optional<Map<String, String>> result = service.findRawRowByBarcode("123");

        assertTrue(result.isPresent());
        assertEquals(expectedRow, result.get());
    }

    @Test
    void findRawRowByBarcode_tripsWhitespace() {
        Map<String, String> expectedRow = Map.of("code", "123");
        when(mockBucketFinder.findBucketForBarcode(eq("123"), any())).thenReturn(Optional.of(sampleBucket));
        when(mockBucketScanner.findRowByBarcode(sampleBucket, "123")).thenReturn(Optional.of(expectedRow));

        Optional<Map<String, String>> result = service.findRawRowByBarcode("  123  ");

        assertTrue(result.isPresent());
    }

    // --- findRawRowByProductIndex ---

    @Test
    void findRawRowByProductIndex_nullIndex_returnsEmpty() {
        Optional<Map<String, String>> result = service.findRawRowByProductIndex(null);
        assertFalse(result.isPresent());
        verify(mockManifestLoader, never()).loadBuckets();
    }

    @Test
    void findRawRowByProductIndex_zeroIndex_returnsEmpty() {
        Optional<Map<String, String>> result = service.findRawRowByProductIndex(0L);
        assertFalse(result.isPresent());
        verify(mockManifestLoader, never()).loadBuckets();
    }

    @Test
    void findRawRowByProductIndex_negativeIndex_returnsEmpty() {
        Optional<Map<String, String>> result = service.findRawRowByProductIndex(-1L);
        assertFalse(result.isPresent());
        verify(mockManifestLoader, never()).loadBuckets();
    }

    @Test
    void findRawRowByProductIndex_bucketNotFound_returnsEmpty() {
        when(mockBucketFinder.findBucketForProductIndex(eq(5L), any())).thenReturn(Optional.empty());

        Optional<Map<String, String>> result = service.findRawRowByProductIndex(5L);

        assertFalse(result.isPresent());
        verify(mockBucketScanner, never()).findRowByProductIndex(any(), any());
    }

    @Test
    void findRawRowByProductIndex_bucketFound_delegatesToScanner() {
        Map<String, String> expectedRow = Map.of("product_index", "5", "product_name", "Test");
        when(mockBucketFinder.findBucketForProductIndex(eq(5L), any())).thenReturn(Optional.of(sampleBucket));
        when(mockBucketScanner.findRowByProductIndex(sampleBucket, 5L)).thenReturn(Optional.of(expectedRow));

        Optional<Map<String, String>> result = service.findRawRowByProductIndex(5L);

        assertTrue(result.isPresent());
        assertEquals(expectedRow, result.get());
    }

    // --- findRawRowsByProductIndices ---

    @Test
    void findRawRowsByProductIndices_nullCollection_returnsEmptyMap() {
        Map<Long, Map<String, String>> result = service.findRawRowsByProductIndices(null);
        assertTrue(result.isEmpty());
        verify(mockManifestLoader, never()).loadBuckets();
    }

    @Test
    void findRawRowsByProductIndices_emptyCollection_returnsEmptyMap() {
        Map<Long, Map<String, String>> result = service.findRawRowsByProductIndices(List.of());
        assertTrue(result.isEmpty());
        verify(mockManifestLoader, never()).loadBuckets();
    }

    @Test
    void findRawRowsByProductIndices_allNullOrNegative_returnsEmptyMap() {
        when(mockBucketFinder.findBucketForProductIndex(any(), any())).thenReturn(Optional.empty());

        Collection<Long> indices = List.of(-1L, 0L);
        Map<Long, Map<String, String>> result = service.findRawRowsByProductIndices(indices);

        assertTrue(result.isEmpty());
        verify(mockManifestLoader, never()).loadBuckets();
    }

    @Test
    void findRawRowsByProductIndices_validIndices_delegatesToScanner() {
        Map<Long, Map<String, String>> scannerResult = Map.of(
                5L, Map.of("product_name", "A"),
                10L, Map.of("product_name", "B")
        );
        when(mockBucketFinder.findBucketForProductIndex(any(), any())).thenReturn(Optional.of(sampleBucket));
        when(mockBucketScanner.findRowsByProductIndices(eq(sampleBucket), any())).thenReturn(scannerResult);

        Map<Long, Map<String, String>> result = service.findRawRowsByProductIndices(List.of(5L, 10L));

        assertEquals(2, result.size());
        assertEquals("A", result.get(5L).get("product_name"));
    }
}
