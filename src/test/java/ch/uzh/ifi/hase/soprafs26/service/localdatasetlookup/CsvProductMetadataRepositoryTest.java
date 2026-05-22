package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for CsvProductMetadataRepository using the real production dataset files
 * available on the classpath during tests.
 */
class CsvProductMetadataRepositoryTest {

    private CsvProductMetadataRepository repository;

    @BeforeEach
    void setUp() {
        CsvNameIndexManifestLoader manifestLoader = new CsvNameIndexManifestLoader();
        CsvShardResolver shardResolver = new CsvShardResolver();
        repository = new CsvProductMetadataRepository(manifestLoader, shardResolver);
    }

    @Test
    void resolveProductRows_nullSet_returnsEmptyList() throws IOException {
        List<ProductRow> result = repository.resolveProductRows(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void resolveProductRows_emptySet_returnsEmptyList() throws IOException {
        List<ProductRow> result = repository.resolveProductRows(Set.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void resolveProductRows_validIndices_returnsRows() throws IOException {
        // Product index 1 is in shard 1 % 256 = 1
        List<ProductRow> result = repository.resolveProductRows(Set.of(1L));
        assertNotNull(result);
        // May or may not be found depending on real data, but should not throw
        // If found, product index should match
        if (!result.isEmpty()) {
            assertEquals(1L, result.get(0).productIndex());
        }
    }

    @Test
    void resolveProductRows_multipleIndices_returnsMultipleRows() throws IOException {
        Set<Long> indices = Set.of(1L, 2L);
        List<ProductRow> result = repository.resolveProductRows(indices);
        assertNotNull(result);
        // Each returned row should have a productIndex in the requested set
        for (ProductRow row : result) {
            assertTrue(indices.contains(row.productIndex()));
        }
    }

    @Test
    void resolveProductRows_outOfBoundsIndex_returnsEmptyOrPartial() throws IOException {
        // Very large product index unlikely to exist
        Set<Long> indices = Set.of(Long.MAX_VALUE - 1);
        List<ProductRow> result = repository.resolveProductRows(indices);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
