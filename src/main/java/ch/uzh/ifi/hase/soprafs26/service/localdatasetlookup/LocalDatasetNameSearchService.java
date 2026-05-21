package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductSearchCandidateDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductSearchResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class LocalDatasetNameSearchService {

  private static final Logger log = LoggerFactory.getLogger(LocalDatasetNameSearchService.class);

  private static final int TARGET_CANDIDATE_COUNT = 1000;
  private static final int QUERY_CANDIDATE_LIMIT = TARGET_CANDIDATE_COUNT + 1;
  private static final int DEFAULT_RETURN_LIMIT = 10;
  private static final int MAX_RETURN_LIMIT = 20;

  private final NameSearchTextNormalizer normalizer;
  private final NameSearchIndexRepository indexRepository;
  private final LocalDatasetProductIndexResolver productIndexResolver;
  private final NameSearchScorer scorer;

  public LocalDatasetNameSearchService(
      NameSearchTextNormalizer normalizer,
      NameSearchIndexRepository indexRepository,
      LocalDatasetProductIndexResolver productIndexResolver,
      NameSearchScorer scorer
  ) {
    this.normalizer = normalizer;
    this.indexRepository = indexRepository;
    this.productIndexResolver = productIndexResolver;
    this.scorer = scorer;
  }

  public LocalDatasetProductSearchResponseDTO search(String rawQuery, int requestedLimit) {
    long startedAt = System.nanoTime();
    List<String> tokens = normalizer.tokenize(rawQuery);
    long tokenizedAt = System.nanoTime();
    int limit = sanitizeLimit(requestedLimit);
    LocalDatasetProductSearchResponseDTO response = createBaseResponse(rawQuery, tokens);

    if (tokens.isEmpty()) {
      return respondNotEnoughInformation(response);
    }

    try {
      long indexReadyAt = System.nanoTime();
      List<TokenInfo> knownTokens = indexRepository.loadTokenInfo(tokens);
      long tokenInfoLoadedAt = System.nanoTime();
      if (knownTokens.isEmpty()) {
        return respondNoKnownTokens(response, tokens);
      }

      CandidateSelection selection = indexRepository.selectAnchorsAndCandidates(
          knownTokens,
          TARGET_CANDIDATE_COUNT,
          QUERY_CANDIDATE_LIMIT
      );
      long candidateSelectionDoneAt = System.nanoTime();
      populateSelectionMetadata(response, selection, tokens);

      if (selection.candidates().isEmpty()) {
        return respondNoMatch(response);
      }
      if (selection.tooBroad()) {
        return attachTooManyMatchesSample(
            response,
            selection,
            tokens,
            limit,
            startedAt,
            tokenizedAt,
            indexReadyAt,
            tokenInfoLoadedAt,
            candidateSelectionDoneAt
        );
      }

      return rankAndAttachCandidates(
          response,
          selection,
          tokens,
          limit,
          startedAt,
          tokenizedAt,
          indexReadyAt,
          tokenInfoLoadedAt,
          candidateSelectionDoneAt
      );
    }
    catch (IOException | RuntimeException ex) {
      log.warn("Local dataset name search failed.", ex);
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Local product name search is unavailable. Check local-dataset/name-index/manifest.json and CSV/GZIP shards.",
          ex
      );
    }
  }

  private LocalDatasetProductSearchResponseDTO rankAndAttachCandidates(
      LocalDatasetProductSearchResponseDTO response,
      CandidateSelection selection,
      List<String> tokens,
      int limit,
      long startedAt,
      long tokenizedAt,
      long indexReadyAt,
      long tokenInfoLoadedAt,
      long candidateSelectionDoneAt
  ) throws IOException {
    List<String> anchorTokens = response.getAnchorTokens();
    List<String> auxiliaryTokens = response.getAuxiliaryTokens();

    List<ProductRow> productRows = productIndexResolver.resolveProductRows(selection.candidates());
    long productRowsResolvedAt = System.nanoTime();

    List<ProductRow> rankingPool = productRows;
    long auxiliaryFilterDoneAt = System.nanoTime();

    List<LocalDatasetProductSearchCandidateDTO> candidates = rankingPool.stream()
        .map(product -> scorer.score(product, tokens, anchorTokens, auxiliaryTokens))
        .sorted(Comparator.comparingDouble(ScoredProduct::score).reversed())
        .limit(limit)
        .map(ScoredProduct::toDto)
        .toList();
    long scoringDoneAt = System.nanoTime();

    log.info(
        "[NAME_SEARCH_TIMING] query='{}' tokens={} anchorTokens={} auxiliaryTokens={} rawCandidates={} resolvedRows={} rankingRows={} "
            + "tokenizeMs={} indexReadyMs={} tokenInfoMs={} candidateSelectMs={} resolveRowsMs={} auxRankMs={} scoreSortMs={} totalMs={}",
        response.getQuery(),
        tokens,
        anchorTokens,
        auxiliaryTokens,
        selection.candidates().size(),
        productRows.size(),
        rankingPool.size(),
        ms(tokenizedAt - startedAt),
        ms(indexReadyAt - tokenizedAt),
        ms(tokenInfoLoadedAt - indexReadyAt),
        ms(candidateSelectionDoneAt - tokenInfoLoadedAt),
        ms(productRowsResolvedAt - candidateSelectionDoneAt),
        ms(auxiliaryFilterDoneAt - productRowsResolvedAt),
        ms(scoringDoneAt - auxiliaryFilterDoneAt),
        ms(scoringDoneAt - startedAt)
    );

    if (candidates.isEmpty()) {
      response.setStatus("NO_MATCH");
      response.setMessage(resolveEmptyCandidateMessage(productRows));
      return response;
    }

    response.setStatus("OK");
    response.setMessage("Choose one of the matching local dataset products.");
    response.setCandidates(candidates);
    return response;
  }

  private LocalDatasetProductSearchResponseDTO attachTooManyMatchesSample(
      LocalDatasetProductSearchResponseDTO response,
      CandidateSelection selection,
      List<String> tokens,
      int limit,
      long startedAt,
      long tokenizedAt,
      long indexReadyAt,
      long tokenInfoLoadedAt,
      long candidateSelectionDoneAt
  ) throws IOException {
    long productRowsResolvedAt;
    long scoringDoneAt;
    Set<Long> sampledProductIndices = stableSample(selection.candidates(), response.getNormalizedQuery(), limit);
    List<ProductRow> sampledRows = productIndexResolver.resolveProductRows(sampledProductIndices);
    productRowsResolvedAt = System.nanoTime();

    List<LocalDatasetProductSearchCandidateDTO> candidates = sampledRows.stream()
        .map(product -> scorer.score(product, tokens, response.getAnchorTokens(), response.getAuxiliaryTokens()))
        .sorted(Comparator.comparingDouble(ScoredProduct::score).reversed())
        .map(ScoredProduct::toDto)
        .toList();
    scoringDoneAt = System.nanoTime();

    log.info(
        "[NAME_SEARCH_TIMING] query='{}' status=TOO_MANY_MATCHES tokens={} anchorTokens={} auxiliaryTokens={} rawCandidates={} sampledRows={} "
            + "tokenizeMs={} indexReadyMs={} tokenInfoMs={} candidateSelectMs={} resolveRowsMs={} scoreSortMs={} totalMs={}",
        response.getQuery(),
        tokens,
        response.getAnchorTokens(),
        response.getAuxiliaryTokens(),
        selection.candidates().size(),
        sampledRows.size(),
        ms(tokenizedAt - startedAt),
        ms(indexReadyAt - tokenizedAt),
        ms(tokenInfoLoadedAt - indexReadyAt),
        ms(candidateSelectionDoneAt - tokenInfoLoadedAt),
        ms(productRowsResolvedAt - candidateSelectionDoneAt),
        ms(scoringDoneAt - productRowsResolvedAt),
        ms(scoringDoneAt - startedAt)
    );

    response.setStatus("TOO_MANY_MATCHES");
    response.setMessage("Too many potential matches. Please provide the full name of the item or use barcode lookup.");
    response.setCandidates(candidates);
    return response;
  }

  private Set<Long> stableSample(Set<Long> productIndices, String normalizedQuery, int limit) {
    if (productIndices == null || productIndices.isEmpty()) {
      return Set.of();
    }

    return productIndices.stream()
        .sorted(Comparator.comparingLong(productIndex -> stableSampleKey(normalizedQuery, productIndex)))
        .limit(limit)
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
  }

  private long stableSampleKey(String normalizedQuery, Long productIndex) {
    String value = (normalizedQuery == null ? "" : normalizedQuery) + ":" + productIndex;
    long hash = 1125899906842597L;
    for (int i = 0; i < value.length(); i += 1) {
      hash = 31 * hash + value.charAt(i);
    }
    return hash == Long.MIN_VALUE ? 0 : Math.abs(hash);
  }

  private void populateSelectionMetadata(
      LocalDatasetProductSearchResponseDTO response,
      CandidateSelection selection,
      List<String> originalTokens
  ) {
    List<String> anchorTokens = reorderByOriginalTokenOrder(selection.anchorTokens(), originalTokens);
    List<String> auxiliaryTokens = originalTokens.stream()
        .filter(token -> !selection.anchorTokens().contains(token))
        .distinct()
        .toList();

    response.setAnchorTokens(anchorTokens);
    response.setAuxiliaryTokens(auxiliaryTokens);
    response.setTotalCandidateCount(Math.min(selection.candidates().size(), QUERY_CANDIDATE_LIMIT));
  }

  private LocalDatasetProductSearchResponseDTO createBaseResponse(String rawQuery, List<String> tokens) {
    LocalDatasetProductSearchResponseDTO response = new LocalDatasetProductSearchResponseDTO();
    response.setQuery(rawQuery);
    response.setNormalizedQuery(String.join(" ", tokens));
    return response;
  }

  private LocalDatasetProductSearchResponseDTO respondNotEnoughInformation(LocalDatasetProductSearchResponseDTO response) {
    response.setStatus("NOT_ENOUGH_INFORMATION");
    response.setMessage("Please enter at least one searchable product word.");
    response.setTotalCandidateCount(0);
    return response;
  }

  private LocalDatasetProductSearchResponseDTO respondNoKnownTokens(
      LocalDatasetProductSearchResponseDTO response,
      List<String> tokens
  ) {
    response.setStatus("NO_MATCH");
    response.setMessage("No local dataset products matched the submitted words.");
    response.setTotalCandidateCount(0);
    response.setAuxiliaryTokens(tokens);
    return response;
  }

  private LocalDatasetProductSearchResponseDTO respondNoMatch(LocalDatasetProductSearchResponseDTO response) {
    response.setStatus("NO_MATCH");
    response.setMessage("No local dataset products matched the submitted words.");
    return response;
  }

  private String resolveEmptyCandidateMessage(List<ProductRow> productRows) {
    if (!productRows.isEmpty()) {
      return "The token index found products, but no product candidates could be prepared for display.";
    }
    return "The token index found products, but product_metadata rows could not be resolved by product_index.";
  }

  private List<String> reorderByOriginalTokenOrder(Set<String> selectedTokens, List<String> originalTokens) {
    return originalTokens.stream()
        .filter(selectedTokens::contains)
        .distinct()
        .toList();
  }

  private double ms(long nanos) {
    return nanos / 1_000_000.0;
  }

  private int sanitizeLimit(int requestedLimit) {
    if (requestedLimit <= 0) {
      return DEFAULT_RETURN_LIMIT;
    }
    return Math.min(requestedLimit, MAX_RETURN_LIMIT);
  }
}
