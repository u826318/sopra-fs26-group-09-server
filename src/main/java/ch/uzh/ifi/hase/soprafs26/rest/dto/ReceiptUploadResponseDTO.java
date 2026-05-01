package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.util.List;
import java.util.Map;

public class ReceiptUploadResponseDTO {
  private Long householdId;
  private String status;
  private String merchantName;
  private String merchantPhoneNumber;
  private String merchantAddress;
  private String transactionDate;
  private String transactionTime;
  private String subtotal;
  private String tax;
  private String total;
  private String tip;
  private String receiptType;
  private String currencyCode;
  private String countryRegion;
  private String rawText;
  private List<ReceiptMatchedItemDTO> items;
  private Map<String, Object> extractedFields;
  private Map<String, Object> rawResult;

  public Long getHouseholdId() {
    return householdId;
  }

  public void setHouseholdId(Long householdId) {
    this.householdId = householdId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getMerchantName() {
    return merchantName;
  }

  public void setMerchantName(String merchantName) {
    this.merchantName = merchantName;
  }

  public String getMerchantPhoneNumber() {
    return merchantPhoneNumber;
  }

  public void setMerchantPhoneNumber(String merchantPhoneNumber) {
    this.merchantPhoneNumber = merchantPhoneNumber;
  }

  public String getMerchantAddress() {
    return merchantAddress;
  }

  public void setMerchantAddress(String merchantAddress) {
    this.merchantAddress = merchantAddress;
  }

  public String getTransactionDate() {
    return transactionDate;
  }

  public void setTransactionDate(String transactionDate) {
    this.transactionDate = transactionDate;
  }

  public String getTransactionTime() {
    return transactionTime;
  }

  public void setTransactionTime(String transactionTime) {
    this.transactionTime = transactionTime;
  }

  public String getSubtotal() {
    return subtotal;
  }

  public void setSubtotal(String subtotal) {
    this.subtotal = subtotal;
  }

  public String getTax() {
    return tax;
  }

  public void setTax(String tax) {
    this.tax = tax;
  }

  public String getTotal() {
    return total;
  }

  public void setTotal(String total) {
    this.total = total;
  }

  public String getTip() {
    return tip;
  }

  public void setTip(String tip) {
    this.tip = tip;
  }

  public String getReceiptType() {
    return receiptType;
  }

  public void setReceiptType(String receiptType) {
    this.receiptType = receiptType;
  }

  public String getCurrencyCode() {
    return currencyCode;
  }

  public void setCurrencyCode(String currencyCode) {
    this.currencyCode = currencyCode;
  }

  public String getCountryRegion() {
    return countryRegion;
  }

  public void setCountryRegion(String countryRegion) {
    this.countryRegion = countryRegion;
  }

  public String getRawText() {
    return rawText;
  }

  public void setRawText(String rawText) {
    this.rawText = rawText;
  }

  public List<ReceiptMatchedItemDTO> getItems() {
    return items;
  }

  public void setItems(List<ReceiptMatchedItemDTO> items) {
    this.items = items;
  }

  public Map<String, Object> getExtractedFields() {
    return extractedFields;
  }

  public void setExtractedFields(Map<String, Object> extractedFields) {
    this.extractedFields = extractedFields;
  }

  public Map<String, Object> getRawResult() {
    return rawResult;
  }

  public void setRawResult(Map<String, Object> rawResult) {
    this.rawResult = rawResult;
  }
}
