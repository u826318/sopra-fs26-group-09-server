package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import java.math.BigInteger;

public record LocalDatasetBucket(
    int bucketId,
    String filename,
    long rowCount,
    String minBarcode,
    String maxBarcode,
    Long minProductIndex,
    Long maxProductIndex
) {  
  public boolean containsBarcode(String barcode) {

    BigInteger barcodeNumber = new BigInteger(barcode);
    BigInteger minNumber = new BigInteger(minBarcode);
    BigInteger maxNumber = new BigInteger(maxBarcode);

    return barcodeNumber.compareTo(minNumber) >= 0
        && barcodeNumber.compareTo(maxNumber) <= 0;
  }

  public boolean containsProductIndex(Long productIndex) {
    if (productIndex == null || minProductIndex == null || maxProductIndex == null) {
      return false;
    }

    return productIndex >= minProductIndex && productIndex <= maxProductIndex;
  }
}
