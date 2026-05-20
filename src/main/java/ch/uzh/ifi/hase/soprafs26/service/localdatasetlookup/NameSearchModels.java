package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductSearchCandidateDTO;

import java.util.Set;

record TokenInfo(String token, Long tokenId, Integer productCount) {}

record CandidateSelection(Set<Long> candidates, Set<String> anchorTokens, boolean tooBroad) {}

record ProductRow(
    Long productIndex,
    String barcode,
    String name,
    String brand,
    String quantity,
    String searchText
) {}

record AnchorSpan(boolean inOrder, int startIndex, int endIndex) {
  int width() {
    if (!inOrder || startIndex < 0 || endIndex < startIndex) {
      return Integer.MAX_VALUE;
    }
    return endIndex - startIndex;
  }
}

record ScoredProduct(ProductRow product, double score) {
  LocalDatasetProductSearchCandidateDTO toDto() {
    LocalDatasetProductSearchCandidateDTO dto = new LocalDatasetProductSearchCandidateDTO();
    dto.setProductIndex(product.productIndex());
    dto.setBarcode(product.barcode());
    dto.setName(product.name());
    dto.setBrand(product.brand());
    dto.setQuantity(product.quantity());
    dto.setScore(Math.round(score * 1000.0) / 1000.0);
    return dto;
  }
}
