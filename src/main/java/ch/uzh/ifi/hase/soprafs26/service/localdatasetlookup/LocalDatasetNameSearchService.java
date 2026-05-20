package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductSearchCandidateDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductSearchResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
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
  private final NameSearchAuxiliaryFilter auxiliaryFilter;
  private final NameSearchScorer scorer;

  public LocalDatasetNameSearchService(
      NameSearchTextNormalizer normalizer,
      NameSearchIndexRepository indexRepository,
      LocalDatasetProductIndexResolver productIndexResolver,
      NameSearchAuxiliaryFilter auxiliaryFilter,
      NameSearchScorer scorer
  ) {
    this.normalizer = normalizer;
    this.indexRepository = indexRepository;
    this.productIndexResolver = productIndexResolver;
    this.auxiliaryFilter = auxiliaryFilter;
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

    try (Connection connection = indexRepository.openConnection()) {
      long connectionOpenedAt = System.nanoTime();
      List<TokenInfo> knownTokens = indexRepository.loadTokenInfo(connection, tokens);
      long tokenInfoLoadedAt = System.nanoTime();
      if (knownTokens.isEmpty()) {
        return respondNoKnownTokens(response, tokens);
      }

      CandidateSelection selection = indexRepository.selectAnchorsAndCandidates(
          connection,
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
        return respondTooBroad(response);
      }

      return rankAndAttachCandidates(
          connection,
          response,
          selection,
          tokens,
          limit,
          startedAt,
          tokenizedAt,
          connectionOpenedAt,
          tokenInfoLoadedAt,
          candidateSelectionDoneAt
      );
    }
    catch (SQLException | IOException | RuntimeException ex) {
      log.warn("Local dataset name search failed.", ex);
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Local product name search is unavailable. Check sqlite-jdbc, local-dataset/name-index.sqlite, and product_metadata table.",
          ex
      );
    }
  }

  private LocalDatasetProductSearchResponseDTO rankAndAttachCandidates(
      Connection connection,
      LocalDatasetProductSearchResponseDTO response,
      CandidateSelection selection,
      List<String> tokens,
      int limit,
      long startedAt,
      long tokenizedAt,
      long connectionOpenedAt,
      long tokenInfoLoadedAt,
      long candidateSelectionDoneAt
  ) throws SQLException {
    List<String> anchorTokens = response.getAnchorTokens();
    List<String> auxiliaryTokens = response.getAuxiliaryTokens();

    List<ProductRow> productRows = productIndexResolver.resolveProductRows(connection, selection.candidates());
    long productRowsResolvedAt = System.nanoTime();

    List<ProductRow> rankingPool = auxiliaryFilter.apply(productRows, anchorTokens, auxiliaryTokens);
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
            + "tokenizeMs={} openSqliteMs={} tokenInfoMs={} candidateSelectMs={} resolveRowsMs={} auxFilterMs={} scoreSortMs={} totalMs={}",
        response.getQuery(),
        tokens,
        anchorTokens,
        auxiliaryTokens,
        selection.candidates().size(),
        productRows.size(),
        rankingPool.size(),
        ms(tokenizedAt - startedAt),
        ms(connectionOpenedAt - tokenizedAt),
        ms(tokenInfoLoadedAt - connectionOpenedAt),
        ms(candidateSelectionDoneAt - tokenInfoLoadedAt),
        ms(productRowsResolvedAt - candidateSelectionDoneAt),
        ms(auxiliaryFilterDoneAt - productRowsResolvedAt),
        ms(scoringDoneAt - auxiliaryFilterDoneAt),
        ms(scoringDoneAt - startedAt)
    );

    if (candidates.isEmpty()) {
      response.setStatus("NO_MATCH");
      response.setMessage(resolveEmptyCandidateMessage(auxiliaryTokens, productRows));
      return response;
    }

    response.setStatus("OK");
    response.setMessage("Choose one of the matching local dataset products.");
    response.setCandidates(candidates);
    return response;
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

  private LocalDatasetProductSearchResponseDTO respondTooBroad(LocalDatasetProductSearchResponseDTO response) {
    response.setStatus("TOO_BROAD");
    response.setMessage("Too many products matched this search. Please provide a more specific product name, brand, or package detail.");
    return response;
  }

  private String resolveEmptyCandidateMessage(List<String> auxiliaryTokens, List<ProductRow> productRows) {
    if (!auxiliaryTokens.isEmpty() && !productRows.isEmpty()) {
      return "No local dataset products matched the remaining shorthand letters after the anchor words were applied.";
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
