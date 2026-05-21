package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class NameSearchAuxiliaryFilter {

  private final NameSearchTextNormalizer normalizer;

  public NameSearchAuxiliaryFilter(NameSearchTextNormalizer normalizer) {
    this.normalizer = normalizer;
  }

  public List<ProductRow> apply(
      List<ProductRow> productRows,
      List<String> anchorTokens,
      List<String> auxiliaryTokens
  ) {
    String compactAuxiliary = normalizer.compactText(String.join("", auxiliaryTokens));
    if (compactAuxiliary.isBlank()) {
      return productRows;
    }

    return productRows.stream()
        .filter(product -> isOrderedSubsequence(
            compactAuxiliary,
            buildCompactAuxiliaryCandidateText(product, anchorTokens)
        ))
        .toList();
  }

  private String buildCompactAuxiliaryCandidateText(ProductRow product, List<String> anchorTokens) {
    String normalized = normalizer.normalizeText(java.util.stream.Stream.of(
            includeBrandForAuxiliaryText(product),
            product.name(),
            product.quantity()
        )
        .filter(value -> value != null && !value.isBlank())
        .collect(Collectors.joining(" ")));

    if (normalized.isBlank()) {
      return "";
    }

    Set<String> anchorTokenSet = new LinkedHashSet<>(anchorTokens);
    String withoutAnchors = java.util.Arrays.stream(normalized.split("\\s+"))
        .filter(token -> !anchorTokenSet.contains(token))
        .collect(Collectors.joining(" "));

    return normalizer.compactText(withoutAnchors);
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

  private boolean isOrderedSubsequence(String needle, String haystack) {
    if (needle == null || needle.isBlank() || haystack == null || haystack.isBlank()) {
      return false;
    }
    int needleIndex = 0;
    for (int i = 0; i < haystack.length() && needleIndex < needle.length(); i += 1) {
      if (haystack.charAt(i) == needle.charAt(needleIndex)) {
        needleIndex += 1;
      }
    }
    return needleIndex == needle.length();
  }
}
