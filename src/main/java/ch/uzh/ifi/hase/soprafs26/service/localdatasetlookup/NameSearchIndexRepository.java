package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class NameSearchIndexRepository {

  private final CsvTokenPostingsRepository tokenPostingsRepository;

  public NameSearchIndexRepository(CsvTokenPostingsRepository tokenPostingsRepository) {
    this.tokenPostingsRepository = tokenPostingsRepository;
  }

  public List<TokenInfo> loadTokenInfo(List<String> tokens) throws IOException {
    Map<String, TokenInfo> infoByToken = new LinkedHashMap<>();

    for (String token : tokens) {
      Optional<TokenInfo> tokenInfo = tokenPostingsRepository.findToken(token);
      tokenInfo.ifPresent(info -> infoByToken.put(token, info));
    }

    return infoByToken.values().stream()
        .sorted(Comparator.comparingInt(TokenInfo::productCount).reversed())
        .toList();
  }

  public CandidateSelection selectAnchorsAndCandidates(
      List<TokenInfo> tokenInfos,
      int targetCandidateCount,
      int queryCandidateLimit
  ) {
    List<TokenInfo> anchors = new ArrayList<>();
    Set<Long> currentCandidates = new LinkedHashSet<>();
    boolean tooBroad = true;

    for (TokenInfo tokenInfo : tokenInfos) {
      anchors.add(tokenInfo);
      currentCandidates = loadIntersectionCandidates(anchors, queryCandidateLimit);

      if (currentCandidates.isEmpty()) {
        return new CandidateSelection(currentCandidates, anchorTokenSet(anchors), false);
      }

      if (currentCandidates.size() <= targetCandidateCount) {
        tooBroad = false;
        break;
      }
    }

    return new CandidateSelection(
        currentCandidates,
        anchorTokenSet(anchors),
        tooBroad && currentCandidates.size() > targetCandidateCount
    );
  }

  private Set<Long> loadIntersectionCandidates(List<TokenInfo> anchors, int limit) {
    if (anchors.isEmpty()) {
      return Collections.emptySet();
    }

    TokenInfo base = anchors.stream()
        .min(Comparator.comparingInt(anchor -> anchor.productIndices().size()))
        .orElse(anchors.get(0));

    List<Set<Long>> otherPostingSets = anchors.stream()
        .filter(anchor -> anchor != base)
        .<Set<Long>>map(anchor -> new LinkedHashSet<>(anchor.productIndices()))
        .toList();

    Set<Long> candidates = new LinkedHashSet<>();
    for (Long productIndex : base.productIndices()) {
      boolean presentInAllAnchors = true;
      for (Set<Long> postingSet : otherPostingSets) {
        if (!postingSet.contains(productIndex)) {
          presentInAllAnchors = false;
          break;
        }
      }

      if (presentInAllAnchors) {
        candidates.add(productIndex);
        if (candidates.size() >= limit) {
          break;
        }
      }
    }

    return candidates;
  }

  private Set<String> anchorTokenSet(List<TokenInfo> anchors) {
    return anchors.stream()
        .map(TokenInfo::token)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
