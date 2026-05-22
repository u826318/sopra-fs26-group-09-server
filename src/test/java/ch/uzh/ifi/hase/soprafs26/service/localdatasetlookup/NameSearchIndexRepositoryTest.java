package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NameSearchIndexRepositoryTest {

    private CsvTokenPostingsRepository mockTokenRepo;
    private NameSearchIndexRepository repository;

    @BeforeEach
    void setUp() {
        mockTokenRepo = mock(CsvTokenPostingsRepository.class);
        repository = new NameSearchIndexRepository(mockTokenRepo);
    }

    // --- loadTokenInfo ---

    @Test
    void loadTokenInfo_noTokensFound_returnsEmptyList() throws IOException {
        when(mockTokenRepo.findToken(any())).thenReturn(Optional.empty());

        List<TokenInfo> result = repository.loadTokenInfo(List.of("apple", "juice"));

        assertTrue(result.isEmpty());
    }

    @Test
    void loadTokenInfo_someTokensFound_returnsSortedByProductCountDescending() throws IOException {
        TokenInfo highCount = new TokenInfo("apple", 100, List.of(1L, 2L, 3L));
        TokenInfo lowCount = new TokenInfo("juice", 10, List.of(2L, 3L));

        when(mockTokenRepo.findToken("apple")).thenReturn(Optional.of(highCount));
        when(mockTokenRepo.findToken("juice")).thenReturn(Optional.of(lowCount));

        List<TokenInfo> result = repository.loadTokenInfo(List.of("apple", "juice"));

        assertEquals(2, result.size());
        assertEquals("apple", result.get(0).token());
        assertEquals("juice", result.get(1).token());
    }

    @Test
    void loadTokenInfo_sortedDescByProductCount() throws IOException {
        TokenInfo t1 = new TokenInfo("milk", 5, List.of(10L));
        TokenInfo t2 = new TokenInfo("cow", 50, List.of(10L, 20L, 30L));

        when(mockTokenRepo.findToken("milk")).thenReturn(Optional.of(t1));
        when(mockTokenRepo.findToken("cow")).thenReturn(Optional.of(t2));

        List<TokenInfo> result = repository.loadTokenInfo(List.of("milk", "cow"));

        assertEquals("cow", result.get(0).token());
        assertEquals("milk", result.get(1).token());
    }

    @Test
    void loadTokenInfo_emptyTokenList_returnsEmpty() throws IOException {
        List<TokenInfo> result = repository.loadTokenInfo(List.of());
        assertTrue(result.isEmpty());
    }

    // --- selectAnchorsAndCandidates ---

    @Test
    void selectAnchorsAndCandidates_singleToken_returnsCandidates() {
        TokenInfo t = new TokenInfo("bread", 3, List.of(1L, 2L, 3L));

        CandidateSelection result = repository.selectAnchorsAndCandidates(List.of(t), 10, 100);

        assertEquals(Set.of(1L, 2L, 3L), result.candidates());
        assertEquals(Set.of("bread"), result.anchorTokens());
        assertFalse(result.tooBroad());
    }

    @Test
    void selectAnchorsAndCandidates_emptyTokenList_returnsEmptyCandidates() {
        CandidateSelection result = repository.selectAnchorsAndCandidates(List.of(), 10, 100);

        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void selectAnchorsAndCandidates_manyTokensNarrowsDown() {
        // targetCandidateCount=2, so "bread" alone (3 items) is too broad.
        // Adding "wheat" narrows to intersection {2,3} which is <= 2 → tooBroad=false
        TokenInfo bread = new TokenInfo("bread", 3, List.of(1L, 2L, 3L));
        TokenInfo wheat = new TokenInfo("wheat", 3, List.of(2L, 3L, 4L));

        CandidateSelection result = repository.selectAnchorsAndCandidates(List.of(bread, wheat), 2, 100);

        assertTrue(result.candidates().contains(2L));
        assertTrue(result.candidates().contains(3L));
        assertFalse(result.candidates().contains(1L));
        assertFalse(result.candidates().contains(4L));
        assertFalse(result.tooBroad());
    }

    @Test
    void selectAnchorsAndCandidates_noIntersection_returnsEmptyCandidates() {
        // "bread" alone has 2 items <= target=10, so it stops at first anchor.
        // No second anchor added. Candidates = bread's postings = {1,2}
        TokenInfo bread = new TokenInfo("bread", 2, List.of(1L, 2L));

        CandidateSelection result = repository.selectAnchorsAndCandidates(List.of(bread), 10, 100);

        assertEquals(Set.of(1L, 2L), result.candidates());
        assertFalse(result.tooBroad());
    }

    @Test
    void selectAnchorsAndCandidates_tooMany_marksTooBroad() {
        // target=2, but single token has 5 candidates
        TokenInfo t = new TokenInfo("bread", 5, List.of(1L, 2L, 3L, 4L, 5L));

        CandidateSelection result = repository.selectAnchorsAndCandidates(List.of(t), 2, 100);

        assertTrue(result.tooBroad());
        assertEquals(5, result.candidates().size());
    }
}
