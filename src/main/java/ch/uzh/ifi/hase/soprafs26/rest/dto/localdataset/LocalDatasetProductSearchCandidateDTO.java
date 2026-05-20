package ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset;

public class LocalDatasetProductSearchCandidateDTO {
  private Long productIndex;
  private String barcode;
  private String name;
  private String brand;
  private String quantity;
  private Double score;

  public Long getProductIndex() { return productIndex; }
  public void setProductIndex(Long productIndex) { this.productIndex = productIndex; }

  public String getBarcode() { return barcode; }
  public void setBarcode(String barcode) { this.barcode = barcode; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public String getBrand() { return brand; }
  public void setBrand(String brand) { this.brand = brand; }

  public String getQuantity() { return quantity; }
  public void setQuantity(String quantity) { this.quantity = quantity; }

  public Double getScore() { return score; }
  public void setScore(Double score) { this.score = score; }
}
