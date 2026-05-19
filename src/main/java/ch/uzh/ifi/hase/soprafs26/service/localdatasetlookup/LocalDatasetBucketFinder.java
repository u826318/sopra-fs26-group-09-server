package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class LocalDatasetBucketFinder {

  private static final Logger log = LoggerFactory.getLogger(LocalDatasetLookupService.class);
  
  public Optional<LocalDatasetBucket> findBucketForBarcode(
      String barcode,
      List<LocalDatasetBucket> buckets
  ) {
    if (barcode == null || buckets == null || buckets.isEmpty()) {
      return Optional.empty();
    }

    for (LocalDatasetBucket bucket : buckets) {
      if (bucket.containsBarcode(barcode)) {
        log.info("\n bucket found: '{}' \n", bucket.bucketId());
        return Optional.of(bucket);
      }
    }

    return Optional.empty();
  }
}
