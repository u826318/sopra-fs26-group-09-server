package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class LocalDatasetBucketFinder {

  public Optional<LocalDatasetBucket> findBucketForBarcode(
      String barcode,
      List<LocalDatasetBucket> buckets
  ) {
    if (barcode == null || buckets == null || buckets.isEmpty()) {
      return Optional.empty();
    }

    for (LocalDatasetBucket bucket : buckets) {
      if (bucket.containsBarcode(barcode)) {
        return Optional.of(bucket);
      }
    }

    return Optional.empty();
  }
}
