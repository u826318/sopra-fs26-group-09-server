package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.util.Map;

public class ReceiptMatchedItemDTO {
  private String description;
  private String quantity;
  private String price;
  private String totalPrice;
  private String productCode;
  private Map<String, Object> rawItem;
  private String matchStatus;
  private String matchSource;
  private ProductDTO matchedProduct;

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getQuantity() {
    return quantity;
  }

  public void setQuantity(String quantity) {
    this.quantity = quantity;
  }

  public String getPrice() {
    return price;
  }

  public void setPrice(String price) {
    this.price = price;
  }

  public String getTotalPrice() {
    return totalPrice;
  }

  public void setTotalPrice(String totalPrice) {
    this.totalPrice = totalPrice;
  }

  public String getProductCode() {
    return productCode;
  }

  public void setProductCode(String productCode) {
    this.productCode = productCode;
  }

  public Map<String, Object> getRawItem() {
    return rawItem;
  }

  public void setRawItem(Map<String, Object> rawItem) {
    this.rawItem = rawItem;
  }

  public String getMatchStatus() {
    return matchStatus;
  }

  public void setMatchStatus(String matchStatus) {
    this.matchStatus = matchStatus;
  }

  public String getMatchSource() {
    return matchSource;
  }

  public void setMatchSource(String matchSource) {
    this.matchSource = matchSource;
  }

  public ProductDTO getMatchedProduct() {
    return matchedProduct;
  }

  public void setMatchedProduct(ProductDTO matchedProduct) {
    this.matchedProduct = matchedProduct;
  }
}
