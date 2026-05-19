package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class LocalDatasetLookupService {

  private final LocalDatasetManifestLoader manifestLoader;
  private final LocalDatasetBucketFinder bucketFinder;
  private final LocalDatasetBucketScanner bucketScanner;

  private static final Logger log = LoggerFactory.getLogger(LocalDatasetLookupService.class);

  public LocalDatasetLookupService(
      LocalDatasetManifestLoader manifestLoader,
      LocalDatasetBucketFinder bucketFinder,
      LocalDatasetBucketScanner bucketScanner
  ) {
    this.manifestLoader = manifestLoader;
    this.bucketFinder = bucketFinder;
    this.bucketScanner = bucketScanner;
  }

  public Optional<Map<String, String>> findRawRowByBarcode(String barcode) {
    String normalizedBarcode = normalizeBarcode(barcode);

    if (normalizedBarcode == null) {
      log.info("\nnormalized barcode is null.\n");
      return Optional.empty();
    }

    List<LocalDatasetBucket> buckets = manifestLoader.loadBuckets();

    Optional<LocalDatasetBucket> maybeBucket =
        bucketFinder.findBucketForBarcode(normalizedBarcode, buckets);

    if (maybeBucket.isEmpty()) {
      log.info("\nmaybeBucket is empty.\n");
      return Optional.empty();
    }

    LocalDatasetBucket bucket = maybeBucket.get();

    return bucketScanner.findRowByBarcode(bucket, normalizedBarcode);
  }

  private String normalizeBarcode(String barcode) {
    if (barcode == null || barcode.isBlank()) {
      return null;
    }

    return barcode.trim();
  }
}