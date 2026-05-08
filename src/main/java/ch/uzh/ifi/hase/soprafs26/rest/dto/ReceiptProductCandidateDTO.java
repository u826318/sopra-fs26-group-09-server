package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class ReceiptProductCandidateDTO {
  private ProductDTO product;
  private Double score;
  private String confidence;
  private String matchSource;
  private ReceiptPantryItemSuggestionDTO suggestedPantryItem;

  public ProductDTO getProduct() {
    return product;
  }

  public void setProduct(ProductDTO product) {
    this.product = product;
  }

  public Double getScore() {
    return score;
  }

  public void setScore(Double score) {
    this.score = score;
  }

  public String getConfidence() {
    return confidence;
  }

  public void setConfidence(String confidence) {
    this.confidence = confidence;
  }

  public String getMatchSource() {
    return matchSource;
  }

  public void setMatchSource(String matchSource) {
    this.matchSource = matchSource;
  }

  public ReceiptPantryItemSuggestionDTO getSuggestedPantryItem() {
    return suggestedPantryItem;
  }

  public void setSuggestedPantryItem(ReceiptPantryItemSuggestionDTO suggestedPantryItem) {
    this.suggestedPantryItem = suggestedPantryItem;
  }
}
