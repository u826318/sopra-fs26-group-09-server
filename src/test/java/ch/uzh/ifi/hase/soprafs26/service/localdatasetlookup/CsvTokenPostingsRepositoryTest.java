package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CsvTokenPostingsRepositoryTest {

    private CsvTokenPostingsRepository mockRepository;
    private CsvTokenPostingsRepository realRepository;

    @BeforeEach
    void setUp() {
        // mock-backed instance for null/blank path tests
        mockRepository = new CsvTokenPostingsRepository(
                mock(CsvNameIndexManifestLoader.class),
                mock(CsvShardResolver.class),
                mock(CsvPostingDecoder.class)
        );

        // real instance for integration tests against classpath data
        CsvNameIndexManifestLoader manifestLoader = new CsvNameIndexManifestLoader();
        CsvShardResolver shardResolver = new CsvShardResolver();
        CsvPostingDecoder decoder = new CsvPostingDecoder();
        realRepository = new CsvTokenPostingsRepository(manifestLoader, shardResolver, decoder);
    }

    @Test
    void findToken_nullToken_returnsEmpty() throws IOException {
        Optional<TokenInfo> result = mockRepository.findToken(null);
        assertFalse(result.isPresent());
    }

    @Test
    void findToken_emptyToken_returnsEmpty() throws IOException {
        Optional<TokenInfo> result = mockRepository.findToken("");
        assertFalse(result.isPresent());
    }

    @Test
    void findToken_blankToken_returnsEmpty() throws IOException {
        Optional<TokenInfo> result = mockRepository.findToken("   ");
        assertFalse(result.isPresent());
    }

    @Test
    void findToken_knownToken_returnsTokenInfo() throws IOException {
        // "lighthouse" is in shard_000.csv.gz (observed in production data)
        Optional<TokenInfo> result = realRepository.findToken("lighthouse");
        assertTrue(result.isPresent());
        assertEquals("lighthouse", result.get().token());
        assertTrue(result.get().productCount() > 0);
        assertFalse(result.get().productIndices().isEmpty());
    }

    @Test
    void findToken_unknownToken_returnsEmpty() throws IOException {
        // A token very unlikely to exist in the dataset
        Optional<TokenInfo> result = realRepository.findToken("xyznotaproduct123456789");
        assertFalse(result.isPresent());
    }
}
