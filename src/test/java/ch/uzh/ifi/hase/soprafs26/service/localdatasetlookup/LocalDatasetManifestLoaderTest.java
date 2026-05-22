package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LocalDatasetManifestLoaderTest {

    private LocalDatasetManifestLoader loader;

    @BeforeEach
    void setUp() {
        loader = new LocalDatasetManifestLoader();
    }

    @Test
    void loadBuckets_returnsNonEmptyList() {
        List<LocalDatasetBucket> buckets = loader.loadBuckets();
        assertNotNull(buckets);
        assertFalse(buckets.isEmpty());
    }

    @Test
    void loadBuckets_eachBucketHasNonNullFilenameAndNonNegativeBucketId() {
        List<LocalDatasetBucket> buckets = loader.loadBuckets();
        for (LocalDatasetBucket bucket : buckets) {
            assertNotNull(bucket.filename(), "filename must not be null");
            assertTrue(bucket.bucketId() >= 0, "bucketId must be >= 0");
        }
    }

    @Test
    void loadBuckets_firstBucketHasNonNullMinAndMaxBarcode() {
        List<LocalDatasetBucket> buckets = loader.loadBuckets();
        assertFalse(buckets.isEmpty());
        LocalDatasetBucket first = buckets.get(0);
        assertNotNull(first.minBarcode(), "minBarcode must not be null");
        assertNotNull(first.maxBarcode(), "maxBarcode must not be null");
    }
}
