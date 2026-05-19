package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import java.math.BigInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record LocalDatasetBucket(
    int bucketId,
    String filename,
    long rowCount,
    String minBarcode,
    String maxBarcode
) {  
  public boolean containsBarcode(String barcode) {

    BigInteger barcodeNumber = new BigInteger(barcode);
    BigInteger minNumber = new BigInteger(minBarcode);
    BigInteger maxNumber = new BigInteger(maxBarcode);

    return barcodeNumber.compareTo(minNumber) >= 0
        && barcodeNumber.compareTo(maxNumber) <= 0;
  }
}