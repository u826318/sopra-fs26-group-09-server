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

  private static final Logger log = LoggerFactory.getLogger(LocalDatasetLookupService.class);
  
  public boolean containsBarcode(String barcode) {

    BigInteger barcodeNumber = new BigInteger(barcode);
    BigInteger minNumber = new BigInteger(minBarcode);
    BigInteger maxNumber = new BigInteger(maxBarcode);

    log.info("\n'minBarcode: {}'\n", minNumber);
    log.info("\n'barcode: {}'\n", barcodeNumber);
    log.info("\n'maxBarcode: {}'\n", maxNumber);

    return barcodeNumber.compareTo(minNumber) >= 0
        && barcodeNumber.compareTo(maxNumber) <= 0;
  }
}