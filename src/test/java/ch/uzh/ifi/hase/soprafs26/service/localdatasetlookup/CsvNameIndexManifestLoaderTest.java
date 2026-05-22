package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class CsvNameIndexManifestLoaderTest {

    private CsvNameIndexManifestLoader loader;

    @BeforeEach
    void setUp() {
        loader = new CsvNameIndexManifestLoader();
    }

    @Test
    void loadManifest_returnsNonNullManifestWithNonNullSections() throws IOException {
        CsvNameIndexManifest manifest = loader.loadManifest();
        assertNotNull(manifest);
        assertNotNull(manifest.tokenPostings());
        assertNotNull(manifest.productMetadata());
    }

    @Test
    void loadManifest_calledTwice_returnsSameInstance() throws IOException {
        CsvNameIndexManifest first = loader.loadManifest();
        CsvNameIndexManifest second = loader.loadManifest();
        assertSame(first, second);
    }

    @Test
    void loadManifest_schemaVersionIsNameIndexCsvV1() throws IOException {
        CsvNameIndexManifest manifest = loader.loadManifest();
        assertEquals("name_index_csv_v1", manifest.schemaVersion());
    }

    @Test
    void loadManifest_tokenPostingsShardCountIsPositive() throws IOException {
        CsvNameIndexManifest manifest = loader.loadManifest();
        assertTrue(manifest.tokenPostings().shardCount() > 0);
    }

    @Test
    void loadManifest_productMetadataShardCountIsPositive() throws IOException {
        CsvNameIndexManifest manifest = loader.loadManifest();
        assertTrue(manifest.productMetadata().shardCount() > 0);
    }
}
