package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.util.Map;

public class ReceiptLineItemDTO {
  private String description;
  private String quantity;
  private String price;
  private String totalPrice;
  private String productCode;
  private Map<String, Object> rawItem;

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
}
