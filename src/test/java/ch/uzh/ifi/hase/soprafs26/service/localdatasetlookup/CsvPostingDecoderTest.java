package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvPostingDecoderTest {

    private CsvPostingDecoder decoder;

    @BeforeEach
    void setUp() {
        decoder = new CsvPostingDecoder();
    }

    @Test
    void decode_nullInput_returnsEmptyList() {
        List<Long> result = decoder.decode("delta-space-separated", null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void decode_blankInput_returnsEmptyList() {
        List<Long> result = decoder.decode("delta-space-separated", "   ");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void decode_singleNumber_returnsSingleElementList() {
        List<Long> result = decoder.decode("delta-space-separated", "5");
        assertEquals(List.of(5L), result);
    }

    @Test
    void decode_deltaEncodedList_returnsDecodedList() {
        // "0 3 2" → [0, 0+3=3, 3+2=5]
        List<Long> result = decoder.decode("delta-space-separated", "0 3 2");
        assertEquals(List.of(0L, 3L, 5L), result);
    }

    @Test
    void decode_unsupportedEncoding_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> decoder.decode("unknown-encoding", "1 2 3")
        );
        assertTrue(ex.getMessage().contains("Unsupported token-posting encoding"));
    }

    @Test
    void decode_invalidCharacterInList_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> decoder.decode("delta-space-separated", "1 abc 3")
        );
        assertTrue(ex.getMessage().contains("Invalid delta posting list"));
    }

    @Test
    void decode_emptyString_returnsEmptyList() {
        List<Long> result = decoder.decode("delta-space-separated", "");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void decode_multipleSpaces_handledCorrectly() {
        // Extra whitespace between numbers should be tolerated
        List<Long> result = decoder.decode("delta-space-separated", "1  2  3");
        assertEquals(List.of(1L, 3L, 6L), result);
    }

    @Test
    void decode_largeDeltas_computedCorrectly() {
        // "100 900" → [100, 1000]
        List<Long> result = decoder.decode("delta-space-separated", "100 900");
        assertEquals(List.of(100L, 1000L), result);
    }
}
