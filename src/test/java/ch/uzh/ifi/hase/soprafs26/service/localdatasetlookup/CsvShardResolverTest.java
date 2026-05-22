package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CsvShardResolverTest {

    private CsvShardResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CsvShardResolver();
    }

    // --- productMetadataShard ---

    @Test
    void productMetadataShard_nullProductIndex_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.productMetadataShard(null, 4)
        );
        assertTrue(ex.getMessage().contains("productIndex must not be null"));
    }

    @Test
    void productMetadataShard_zero_returnsShard0() {
        int shard = resolver.productMetadataShard(0L, 4);
        assertEquals(0, shard);
    }

    @Test
    void productMetadataShard_five_returnsShard1() {
        // 5 mod 4 = 1
        int shard = resolver.productMetadataShard(5L, 4);
        assertEquals(1, shard);
    }

    @Test
    void productMetadataShard_exactMultiple_returnsShard0() {
        // 8 mod 4 = 0
        int shard = resolver.productMetadataShard(8L, 4);
        assertEquals(0, shard);
    }

    // --- tokenShard ---

    @Test
    void tokenShard_deterministic_sameInputSameResult() {
        int first = resolver.tokenShard("hello", 10);
        int second = resolver.tokenShard("hello", 10);
        assertEquals(first, second);
    }

    @Test
    void tokenShard_nullToken_doesNotThrow() {
        // null is treated as empty string; should not throw
        assertDoesNotThrow(() -> resolver.tokenShard(null, 10));
    }

    @Test
    void tokenShard_nullAndEmptyString_returnSameValue() {
        // null and "" should produce the same CRC32 → same shard
        int nullShard = resolver.tokenShard(null, 10);
        int emptyShard = resolver.tokenShard("", 10);
        assertEquals(nullShard, emptyShard);
    }

    @Test
    void tokenShard_resultInBounds() {
        // Result must be in [0, shardCount)
        int shardCount = 8;
        int shard = resolver.tokenShard("test-token", shardCount);
        assertTrue(shard >= 0 && shard < shardCount);
    }

    // --- tokenPostingsShardResource ---

    @Test
    void tokenPostingsShardResource_returnsCorrectPath() {
        CsvNameIndexShardConfig config = new CsvNameIndexShardConfig("token-dir", 10, "shard-%03d.csv");
        CsvNameIndexManifest manifest = mock(CsvNameIndexManifest.class);
        when(manifest.tokenPostings()).thenReturn(config);

        String resource = resolver.tokenPostingsShardResource(manifest, "apple");

        assertNotNull(resource);
        assertTrue(resource.startsWith(CsvNameIndexManifestLoader.NAME_INDEX_ROOT + "/token-dir/"));
        assertTrue(resource.endsWith(".csv"));
    }

    // --- productMetadataShardResource ---

    @Test
    void productMetadataShardResource_returnsCorrectPath() {
        CsvNameIndexShardConfig config = new CsvNameIndexShardConfig("meta-dir", 4, "shard-%02d.csv");
        CsvNameIndexManifest manifest = mock(CsvNameIndexManifest.class);
        when(manifest.productMetadata()).thenReturn(config);

        String resource = resolver.productMetadataShardResource(manifest, 5L);

        assertNotNull(resource);
        // 5 mod 4 = 1 → shard 1
        assertTrue(resource.startsWith(CsvNameIndexManifestLoader.NAME_INDEX_ROOT + "/meta-dir/"));
        assertTrue(resource.contains("01"));
    }
}
