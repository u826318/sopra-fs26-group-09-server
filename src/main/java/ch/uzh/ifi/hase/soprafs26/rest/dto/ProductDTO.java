package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.util.List;
import java.util.Map;

public class ProductDTO {
  private String barcode;
  private String name;
  private String brand;
  private String quantity;
  private String servingSize;
  private String imageUrl;
  private String productUrl;
  private String nutriScore;

  private List<String> stores;
  private List<String> storeTags;
  private List<String> purchasePlaces;

  private Map<String, Object> nutriments;
  private Map<String, Object> nutriScoreData;
  private Map<String, Object> rawProduct;

  public String getBarcode() {
    return barcode;
  }

  public void setBarcode(String barcode) {
    this.barcode = barcode;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getBrand() {
    return brand;
  }

  public void setBrand(String brand) {
    this.brand = brand;
  }

  public String getQuantity() {
    return quantity;
  }

  public void setQuantity(String quantity) {
    this.quantity = quantity;
  }

  public String getServingSize() {
    return servingSize;
  }

  public void setServingSize(String servingSize) {
    this.servingSize = servingSize;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public void setImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }

  public String getProductUrl() {
    return productUrl;
  }

  public void setProductUrl(String productUrl) {
    this.productUrl = productUrl;
  }

  public String getNutriScore() {
    return nutriScore;
  }

  public void setNutriScore(String nutriScore) {
    this.nutriScore = nutriScore;
  }

  public List<String> getStores() {
    return stores;
  }

  public void setStores(List<String> stores) {
    this.stores = stores;
  }

  public List<String> getStoreTags() {
    return storeTags;
  }

  public void setStoreTags(List<String> storeTags) {
    this.storeTags = storeTags;
  }

  public List<String> getPurchasePlaces() {
    return purchasePlaces;
  }

  public void setPurchasePlaces(List<String> purchasePlaces) {
    this.purchasePlaces = purchasePlaces;
  }

  public Map<String, Object> getNutriments() {
    return nutriments;
  }

  public void setNutriments(Map<String, Object> nutriments) {
    this.nutriments = nutriments;
  }

  public Map<String, Object> getNutriScoreData() {
    return nutriScoreData;
  }

  public void setNutriScoreData(Map<String, Object> nutriScoreData) {
    this.nutriScoreData = nutriScoreData;
  }

  public Map<String, Object> getRawProduct() {
    return rawProduct;
  }

  public void setRawProduct(Map<String, Object> rawProduct) {
    this.rawProduct = rawProduct;
  }
}