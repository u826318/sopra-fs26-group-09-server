package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
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

  public Optional<Map<String, String>> findRawRowByProductIndex(Long productIndex) {
    if (productIndex == null || productIndex <= 0) {
      return Optional.empty();
    }

    List<LocalDatasetBucket> buckets = manifestLoader.loadBuckets();
    Optional<LocalDatasetBucket> maybeBucket =
        bucketFinder.findBucketForProductIndex(productIndex, buckets);

    return maybeBucket.flatMap(bucket -> bucketScanner.findRowByProductIndex(bucket, productIndex));
  }

  public Map<Long, Map<String, String>> findRawRowsByProductIndices(Collection<Long> productIndices) {
    if (productIndices == null || productIndices.isEmpty()) {
      return Map.of();
    }

    List<Long> cleanedProductIndices = productIndices.stream()
        .filter(index -> index != null && index > 0)
        .distinct()
        .toList();

    if (cleanedProductIndices.isEmpty()) {
      return Map.of();
    }

    List<LocalDatasetBucket> buckets = manifestLoader.loadBuckets();
    Map<LocalDatasetBucket, List<Long>> indicesByBucket = new LinkedHashMap<>();

    for (Long productIndex : cleanedProductIndices) {
      bucketFinder.findBucketForProductIndex(productIndex, buckets)
          .ifPresent(bucket -> indicesByBucket.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(productIndex));
    }

    Map<Long, Map<String, String>> rows = new LinkedHashMap<>();

    for (Map.Entry<LocalDatasetBucket, List<Long>> entry : indicesByBucket.entrySet()) {
      rows.putAll(bucketScanner.findRowsByProductIndices(entry.getKey(), entry.getValue()));
    }

    return rows;
  }

  private String normalizeBarcode(String barcode) {
    if (barcode == null || barcode.isBlank()) {
      return null;
    }

    return barcode.trim();
  }
}
