package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class NameSearchScorer {

  private final NameSearchTextNormalizer normalizer;

  public NameSearchScorer(NameSearchTextNormalizer normalizer) {
    this.normalizer = normalizer;
  }

  public ScoredProduct score(ProductRow product, List<String> allTokens, List<String> anchorTokens, List<String> auxiliaryTokens) {
    String searchText = normalizer.normalizeText(product.searchText() != null ? product.searchText() : product.name());
    String normalizedQuery = String.join(" ", allTokens);
    double score = 0.0;

    AnchorSpan anchorSpan = findAnchorSpan(searchText, anchorTokens);
    long presentAnchors = anchorTokens.stream().filter(searchText::contains).count();
    score += presentAnchors * 100.0;

    if (!anchorTokens.isEmpty() && presentAnchors == anchorTokens.size()) {
      score += 120.0;
    }

    if (anchorSpan.inOrder()) {
      score += 90.0;
      score += Math.max(0.0, 60.0 - anchorSpan.width());
    }

    if (!normalizedQuery.isBlank() && searchText.contains(normalizedQuery)) {
      score += 160.0;
    }

    score += levenshteinSimilarity(normalizedQuery, searchText) * 80.0;
    score += auxiliaryWordPrefixBonus(product, anchorTokens, auxiliaryTokens);
    return new ScoredProduct(product, Double.isFinite(score) ? score : 0.0);
  }


  private double auxiliaryWordPrefixBonus(ProductRow product, List<String> anchorTokens, List<String> auxiliaryTokens) {
    String compactAuxiliary = normalizer.compactText(String.join("", auxiliaryTokens));
    if (compactAuxiliary.isBlank()) {
      return 0.0;
    }

    List<String> candidateWords = buildAuxiliaryCandidateWords(product, anchorTokens);
    if (candidateWords.isEmpty()) {
      return 0.0;
    }

    int matchedWordCount = bestPrefixChunkMatchWordCount(compactAuxiliary, candidateWords);
    if (matchedWordCount <= 0) {
      return 0.0;
    }

    double bonus = 320.0;
    if (matchedWordCount > 1) {
      bonus += 80.0;
    }
    if (matchedWordCount <= 2) {
      bonus += 40.0;
    }
    return bonus;
  }

  private List<String> buildAuxiliaryCandidateWords(ProductRow product, List<String> anchorTokens) {
    String normalized = normalizer.normalizeText(java.util.stream.Stream.of(
            includeBrandForAuxiliaryText(product),
            product.name(),
            product.quantity()
        )
        .filter(value -> value != null && !value.isBlank())
        .collect(Collectors.joining(" ")));

    if (normalized.isBlank()) {
      return List.of();
    }

    Set<String> anchorTokenSet = new HashSet<>(anchorTokens);
    return java.util.Arrays.stream(normalized.split("\\s+"))
        .filter(token -> !token.isBlank())
        .filter(token -> !anchorTokenSet.contains(token))
        .toList();
  }

  private String includeBrandForAuxiliaryText(ProductRow product) {
    if (product.brand() == null || product.brand().isBlank()) {
      return null;
    }

    String normalizedBrand = normalizer.normalizeText(product.brand());
    String normalizedName = normalizer.normalizeText(product.name());
    if (!normalizedBrand.isBlank() && !normalizedName.isBlank() && normalizedName.contains(normalizedBrand)) {
      return null;
    }

    return product.brand();
  }

  private int bestPrefixChunkMatchWordCount(String compactAuxiliary, List<String> candidateWords) {
    int best = 0;
    for (int start = 0; start < candidateWords.size(); start += 1) {
      best = Math.max(best, bestPrefixChunkMatchFrom(compactAuxiliary, candidateWords, start, 0, 0));
    }
    return best;
  }

  private int bestPrefixChunkMatchFrom(
      String compactAuxiliary,
      List<String> candidateWords,
      int wordIndex,
      int auxIndex,
      int usedWords
  ) {
    if (auxIndex == compactAuxiliary.length()) {
      return usedWords;
    }
    if (wordIndex >= candidateWords.size()) {
      return -1;
    }

    String word = normalizer.compactText(candidateWords.get(wordIndex));
    if (word.isBlank()) {
      return bestPrefixChunkMatchFrom(compactAuxiliary, candidateWords, wordIndex + 1, auxIndex, usedWords);
    }

    int best = -1;
    int remainingChars = compactAuxiliary.length() - auxIndex;
    for (int chunkLength = 1; chunkLength <= remainingChars; chunkLength += 1) {
      String chunk = compactAuxiliary.substring(auxIndex, auxIndex + chunkLength);
      if (!word.startsWith(chunk)) {
        continue;
      }
      best = Math.max(
          best,
          bestPrefixChunkMatchFrom(compactAuxiliary, candidateWords, wordIndex + 1, auxIndex + chunkLength, usedWords + 1)
      );
    }
    return best;
  }

  private AnchorSpan findAnchorSpan(String searchText, List<String> anchorTokens) {
    if (anchorTokens.isEmpty()) {
      return new AnchorSpan(false, -1, -1);
    }

    int searchFrom = 0;
    int first = -1;
    int last = -1;
    for (String token : anchorTokens) {
      int index = searchText.indexOf(token, searchFrom);
      if (index < 0) {
        return new AnchorSpan(false, -1, -1);
      }
      if (first < 0) {
        first = index;
      }
      last = index + token.length();
      searchFrom = last;
    }
    return new AnchorSpan(true, first, last);
  }

  private double levenshteinSimilarity(String query, String candidate) {
    if (query.isBlank() || candidate.isBlank()) {
      return 0.0;
    }
    String shortenedCandidate = candidate.length() > 160 ? candidate.substring(0, 160) : candidate;
    int distance = levenshteinDistance(query, shortenedCandidate);
    int maxLength = Math.max(query.length(), shortenedCandidate.length());
    return maxLength == 0 ? 0.0 : Math.max(0.0, 1.0 - ((double) distance / maxLength));
  }

  private int levenshteinDistance(String left, String right) {
    int[] previous = new int[right.length() + 1];
    int[] current = new int[right.length() + 1];
    for (int j = 0; j <= right.length(); j += 1) {
      previous[j] = j;
    }
    for (int i = 1; i <= left.length(); i += 1) {
      current[0] = i;
      for (int j = 1; j <= right.length(); j += 1) {
        int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
        current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
      }
      int[] tmp = previous;
      previous = current;
      current = tmp;
    }
    return previous[right.length()];
  }
}
