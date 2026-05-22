package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductSearchResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LocalDatasetNameSearchServiceTest {

    private NameSearchTextNormalizer normalizer;
    private NameSearchIndexRepository indexRepository;
    private LocalDatasetProductIndexResolver productIndexResolver;
    private NameSearchScorer scorer;

    private LocalDatasetNameSearchService service;

    @BeforeEach
    void setUp() {
        normalizer = mock(NameSearchTextNormalizer.class);
        indexRepository = mock(NameSearchIndexRepository.class);
        productIndexResolver = mock(LocalDatasetProductIndexResolver.class);
        scorer = mock(NameSearchScorer.class);

        service = new LocalDatasetNameSearchService(normalizer, indexRepository, productIndexResolver, scorer);
    }

    // --- NOT_ENOUGH_INFORMATION branch ---

    @Test
    void search_emptyTokens_returnsNotEnoughInformation() {
        when(normalizer.tokenize(anyString())).thenReturn(Collections.emptyList());

        LocalDatasetProductSearchResponseDTO response = service.search("  ", 10);

        assertEquals("NOT_ENOUGH_INFORMATION", response.getStatus());
        assertNotNull(response.getMessage());
        assertEquals(0, response.getTotalCandidateCount());
    }

    @Test
    void search_nullQuery_emptyTokens_returnsNotEnoughInformation() {
        when(normalizer.tokenize(isNull())).thenReturn(Collections.emptyList());

        LocalDatasetProductSearchResponseDTO response = service.search(null, 10);

        assertEquals("NOT_ENOUGH_INFORMATION", response.getStatus());
    }

    // --- NO_MATCH (no known tokens) branch ---

    @Test
    void search_noKnownTokens_returnsNoMatch() throws IOException {
        when(normalizer.tokenize(anyString())).thenReturn(List.of("xyzunknown"));
        when(indexRepository.loadTokenInfo(anyList())).thenReturn(Collections.emptyList());

        LocalDatasetProductSearchResponseDTO response = service.search("xyzunknown", 10);

        assertEquals("NO_MATCH", response.getStatus());
        assertNotNull(response.getMessage());
        assertEquals(0, response.getTotalCandidateCount());
        // auxiliaryTokens should be set to original tokens
        assertTrue(response.getAuxiliaryTokens().contains("xyzunknown"));
    }

    // --- NO_MATCH (empty candidates) branch ---

    @Test
    void search_emptyCandidates_returnsNoMatch() throws IOException {
        when(normalizer.tokenize(anyString())).thenReturn(List.of("apple"));
        TokenInfo tokenInfo = new TokenInfo("apple", 5, List.of(1L, 2L, 3L, 4L, 5L));
        when(indexRepository.loadTokenInfo(anyList())).thenReturn(List.of(tokenInfo));
        // Return empty candidates
        CandidateSelection emptySelection = new CandidateSelection(Collections.emptySet(), Set.of("apple"), false);
        when(indexRepository.selectAnchorsAndCandidates(anyList(), anyInt(), anyInt())).thenReturn(emptySelection);

        LocalDatasetProductSearchResponseDTO response = service.search("apple", 10);

        assertEquals("NO_MATCH", response.getStatus());
    }

    // --- TOO_MANY_MATCHES branch ---

    @Test
    void search_tooBroadSelection_returnsTooManyMatches() throws IOException {
        when(normalizer.tokenize(anyString())).thenReturn(List.of("milk"));
        when(normalizer.normalizeText(anyString())).thenReturn("milk");
        TokenInfo tokenInfo = new TokenInfo("milk", 2000, List.of());
        when(indexRepository.loadTokenInfo(anyList())).thenReturn(List.of(tokenInfo));

        Set<Long> bigSet = Set.of(1L, 2L, 3L, 4L, 5L, 6L);
        CandidateSelection tooBroadSelection = new CandidateSelection(bigSet, Set.of("milk"), true);
        when(indexRepository.selectAnchorsAndCandidates(anyList(), anyInt(), anyInt())).thenReturn(tooBroadSelection);

        ProductRow row1 = new ProductRow(1L, "0001", "Whole Milk", "Brand", "1L", "whole milk brand 1l");
        when(productIndexResolver.resolveProductRows(any())).thenReturn(List.of(row1));

        ScoredProduct scored1 = new ScoredProduct(row1, 100.0);
        when(scorer.score(any(), any(), any(), any())).thenReturn(scored1);

        LocalDatasetProductSearchResponseDTO response = service.search("milk", 10);

        assertEquals("TOO_MANY_MATCHES", response.getStatus());
        assertNotNull(response.getMessage());
        assertFalse(response.getCandidates().isEmpty());
    }

    // --- OK branch ---

    @Test
    void search_normalPath_returnsOkWithCandidates() throws IOException {
        when(normalizer.tokenize(anyString())).thenReturn(List.of("lighthouse"));
        when(normalizer.normalizeText(anyString())).thenReturn("lighthouse");
        TokenInfo tokenInfo = new TokenInfo("lighthouse", 3, List.of(10L, 20L, 30L));
        when(indexRepository.loadTokenInfo(anyList())).thenReturn(List.of(tokenInfo));

        Set<Long> candidates = Set.of(10L, 20L, 30L);
        CandidateSelection selection = new CandidateSelection(candidates, Set.of("lighthouse"), false);
        when(indexRepository.selectAnchorsAndCandidates(anyList(), anyInt(), anyInt())).thenReturn(selection);

        ProductRow row = new ProductRow(10L, "1234567890", "Lighthouse Bread", "Brand X", "500g", "lighthouse bread brand x 500g");
        when(productIndexResolver.resolveProductRows(any())).thenReturn(List.of(row));

        ScoredProduct scored = new ScoredProduct(row, 350.0);
        when(scorer.score(any(), any(), any(), any())).thenReturn(scored);

        LocalDatasetProductSearchResponseDTO response = service.search("lighthouse", 10);

        assertEquals("OK", response.getStatus());
        assertFalse(response.getCandidates().isEmpty());
        assertEquals("Lighthouse Bread", response.getCandidates().get(0).getName());
    }

    @Test
    void search_normalPath_ranksCandidatesByScoreDescending() throws IOException {
        when(normalizer.tokenize(anyString())).thenReturn(List.of("bread"));
        when(normalizer.normalizeText(anyString())).thenReturn("bread");
        TokenInfo tokenInfo = new TokenInfo("bread", 2, List.of(1L, 2L));
        when(indexRepository.loadTokenInfo(anyList())).thenReturn(List.of(tokenInfo));

        CandidateSelection selection = new CandidateSelection(Set.of(1L, 2L), Set.of("bread"), false);
        when(indexRepository.selectAnchorsAndCandidates(anyList(), anyInt(), anyInt())).thenReturn(selection);

        ProductRow rowLow = new ProductRow(1L, "111", "Rye Bread", null, "400g", "rye bread 400g");
        ProductRow rowHigh = new ProductRow(2L, "222", "White Bread", null, "500g", "white bread 500g");
        when(productIndexResolver.resolveProductRows(any())).thenReturn(List.of(rowLow, rowHigh));

        ScoredProduct scoredLow = new ScoredProduct(rowLow, 100.0);
        ScoredProduct scoredHigh = new ScoredProduct(rowHigh, 300.0);
        when(scorer.score(eq(rowLow), any(), any(), any())).thenReturn(scoredLow);
        when(scorer.score(eq(rowHigh), any(), any(), any())).thenReturn(scoredHigh);

        LocalDatasetProductSearchResponseDTO response = service.search("bread", 10);

        assertEquals("OK", response.getStatus());
        assertEquals(2, response.getCandidates().size());
        // Higher score should come first
        assertEquals("White Bread", response.getCandidates().get(0).getName());
        assertEquals("Rye Bread", response.getCandidates().get(1).getName());
    }

    // --- Limit sanitization ---

    @Test
    void search_zeroPlusRequestedLimit_defaultsTo10() throws IOException {
        when(normalizer.tokenize(anyString())).thenReturn(List.of("apple"));
        TokenInfo tokenInfo = new TokenInfo("apple", 1, List.of(1L));
        when(indexRepository.loadTokenInfo(anyList())).thenReturn(List.of(tokenInfo));
        CandidateSelection selection = new CandidateSelection(Set.of(1L), Set.of("apple"), false);
        when(indexRepository.selectAnchorsAndCandidates(anyList(), anyInt(), anyInt())).thenReturn(selection);

        ProductRow row = new ProductRow(1L, "111", "Apple Juice", null, "1L", "apple juice 1l");
        when(productIndexResolver.resolveProductRows(any())).thenReturn(List.of(row));
        ScoredProduct scored = new ScoredProduct(row, 100.0);
        when(scorer.score(any(), any(), any(), any())).thenReturn(scored);

        // Limit 0 → default 10, should not throw
        LocalDatasetProductSearchResponseDTO response = service.search("apple", 0);
        assertEquals("OK", response.getStatus());
    }

    @Test
    void search_requestedLimitExceedsMax_cappedAt20() throws IOException {
        when(normalizer.tokenize(anyString())).thenReturn(List.of("juice"));
        TokenInfo tokenInfo = new TokenInfo("juice", 1, List.of(5L));
        when(indexRepository.loadTokenInfo(anyList())).thenReturn(List.of(tokenInfo));
        CandidateSelection selection = new CandidateSelection(Set.of(5L), Set.of("juice"), false);
        when(indexRepository.selectAnchorsAndCandidates(anyList(), anyInt(), anyInt())).thenReturn(selection);

        ProductRow row = new ProductRow(5L, "555", "Orange Juice", null, "500ml", "orange juice 500ml");
        when(productIndexResolver.resolveProductRows(any())).thenReturn(List.of(row));
        ScoredProduct scored = new ScoredProduct(row, 80.0);
        when(scorer.score(any(), any(), any(), any())).thenReturn(scored);

        // Limit 100 → capped at 20, should not throw
        LocalDatasetProductSearchResponseDTO response = service.search("juice", 100);
        assertEquals("OK", response.getStatus());
    }

    // --- IOException handling ---

    @Test
    void search_ioExceptionFromIndex_throws503() throws IOException {
        when(normalizer.tokenize(anyString())).thenReturn(List.of("cheese"));
        when(indexRepository.loadTokenInfo(anyList())).thenThrow(new IOException("shard read error"));

        assertThrows(ResponseStatusException.class, () -> service.search("cheese", 10));
    }

    // --- query metadata on response ---

    @Test
    void search_setsQueryAndNormalizedQueryOnResponse() {
        when(normalizer.tokenize("Apple Juice")).thenReturn(List.of("apple", "juice"));

        LocalDatasetProductSearchResponseDTO response = service.search("Apple Juice", 10);

        assertEquals("Apple Juice", response.getQuery());
        assertEquals("apple juice", response.getNormalizedQuery());
    }

    // --- OK path: empty ranked candidates → NO_MATCH ---

    @Test
    void search_normalPath_noProductRowsResolved_returnsNoMatch() throws IOException {
        when(normalizer.tokenize(anyString())).thenReturn(List.of("lighthouse"));
        when(normalizer.normalizeText(anyString())).thenReturn("lighthouse");
        TokenInfo tokenInfo = new TokenInfo("lighthouse", 3, List.of(10L, 20L, 30L));
        when(indexRepository.loadTokenInfo(anyList())).thenReturn(List.of(tokenInfo));

        Set<Long> candidates = Set.of(10L, 20L, 30L);
        CandidateSelection selection = new CandidateSelection(candidates, Set.of("lighthouse"), false);
        when(indexRepository.selectAnchorsAndCandidates(anyList(), anyInt(), anyInt())).thenReturn(selection);

        // Resolver returns nothing → candidates list will be empty → NO_MATCH
        when(productIndexResolver.resolveProductRows(any())).thenReturn(Collections.emptyList());

        LocalDatasetProductSearchResponseDTO response = service.search("lighthouse", 10);

        assertEquals("NO_MATCH", response.getStatus());
    }
}
