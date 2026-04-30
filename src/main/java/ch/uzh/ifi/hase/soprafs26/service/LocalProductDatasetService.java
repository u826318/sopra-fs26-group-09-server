package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.rest.dto.ProductDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class LocalProductDatasetService {

  private static final Logger log = LoggerFactory.getLogger(LocalProductDatasetService.class);

  /**
   * Preferred production/classpath location:
   * src/main/resources/local-products/manifest.json
   */
  private static final String MANIFEST_PATH = "local-products/manifest.json";

  /**
   * Preferred production/classpath bucket location:
   * src/main/resources/local-products/buckets/bucket_000.csv
   * src/main/resources/local-products/buckets/bucket_001.csv
   * ...
   */
  private static final String BUCKETS_PATH = "local-products/buckets/";

  private final ObjectMapper objectMapper = new ObjectMapper();

  private volatile List<BucketRange> buckets;
  private volatile String lastManifestDiagnostic = "Manifest has not been loaded yet.";

  @PostConstruct
  public void warmUpManifestDiagnostics() {
    debug("[LOCAL_FALLBACK] startup manifest sanity check begins. cwd='{}'", Paths.get("").toAbsolutePath());
    List<BucketRange> warmBuckets = getBuckets();
    debug("[LOCAL_FALLBACK] startup manifest sanity check finished. loadedBucketCount={}, diagnostic='{}'", warmBuckets.size(), getLastDiagnostics());
  }

  public String getLastDiagnostics() {
    List<BucketRange> currentBuckets = buckets;
    int bucketCount = currentBuckets == null ? 0 : currentBuckets.size();
    return "bucketCount=" + bucketCount + "; " + lastManifestDiagnostic;
  }

  public Optional<ProductDTO> lookupByBarcode(String barcode) {
    debug("[LOCAL_FALLBACK] lookup requested. originalBarcode='{}'", barcode);

    String normalizedBarcode = normalizeBarcode(barcode);
    if (normalizedBarcode == null) {
      debug("[LOCAL_FALLBACK] aborting: barcode is blank or contains no digits. originalBarcode='{}'", barcode);
      return Optional.empty();
    }

    BigInteger targetCode = toBigIntegerOrNull(normalizedBarcode);
    if (targetCode == null) {
      debug("[LOCAL_FALLBACK] aborting: normalized barcode could not be parsed as BigInteger. normalizedBarcode='{}'", normalizedBarcode);
      return Optional.empty();
    }

    debug("[LOCAL_FALLBACK] normalizedBarcode='{}', numericTarget={}", normalizedBarcode, targetCode);

    List<BucketRange> loadedBuckets = getBuckets();
    if (loadedBuckets.isEmpty()) {
      debug("[LOCAL_FALLBACK] aborting: no bucket ranges are loaded. Check whether manifest is reachable at '{}'", MANIFEST_PATH);
      return Optional.empty();
    }

    BucketRange bucket = findBucket(targetCode, loadedBuckets);
    if (bucket == null) {
      debug(
          "[LOCAL_FALLBACK] no manifest bucket contains barcode '{}'. Loaded bucket count={}, firstRange={}, lastRange={}, nearestContext={}",
          normalizedBarcode,
          loadedBuckets.size(),
          describeBucket(loadedBuckets.get(0)),
          describeBucket(loadedBuckets.get(loadedBuckets.size() - 1)),
          describeNearestBuckets(targetCode, loadedBuckets)
      );
      return Optional.empty();
    }

    debug(
        "[LOCAL_FALLBACK] selected bucket for barcode '{}': bucketId={}, filename='{}', range={}..{}, expectedRows={}",
        normalizedBarcode,
        bucket.bucketId,
        bucket.filename,
        bucket.minCode,
        bucket.maxCode,
        bucket.rowCount
    );

    return scanBucket(bucket, targetCode, normalizedBarcode);
  }

  private List<BucketRange> getBuckets() {
    List<BucketRange> currentBuckets = buckets;
    if (currentBuckets != null && !currentBuckets.isEmpty()) {
      debug("[LOCAL_FALLBACK] manifest already loaded in memory. bucketCount={}", currentBuckets.size());
      return currentBuckets;
    }

    if (currentBuckets != null && currentBuckets.isEmpty()) {
      debug("[LOCAL_FALLBACK] previous manifest load produced 0 bucket ranges. Retrying manifest discovery instead of reusing the empty cache.");
    }

    synchronized (this) {
      if (buckets != null && !buckets.isEmpty()) {
        debug("[LOCAL_FALLBACK] manifest already loaded in memory after lock. bucketCount={}", buckets.size());
        return buckets;
      }

      if (buckets != null && buckets.isEmpty()) {
        debug("[LOCAL_FALLBACK] previous manifest load inside lock produced 0 bucket ranges. Retrying manifest discovery now.");
      }

      List<BucketRange> loadedBuckets = loadManifestSafely();
      if (loadedBuckets.isEmpty()) {
        debug("[LOCAL_FALLBACK] manifest discovery returned 0 valid bucket ranges. The empty result will NOT be cached; the next lookup will retry. Check manifest placement and JSON shape.");
        buckets = null;
      }
      else {
        buckets = loadedBuckets;
      }

      return loadedBuckets;
    }
  }

  private List<BucketRange> loadManifestSafely() {
    debug("[LOCAL_FALLBACK] loading manifest. preferredClasspathPath='{}'", MANIFEST_PATH);

    Optional<ResolvedResource> resolvedManifest = resolveResource(MANIFEST_PATH);
    if (resolvedManifest.isEmpty()) {
      lastManifestDiagnostic = "Manifest not found. Tried classpath and filesystem candidates for " + MANIFEST_PATH
          + "; cwd=" + Paths.get("").toAbsolutePath();
      debug("[LOCAL_FALLBACK] manifest not found. {}", lastManifestDiagnostic);
      return new ArrayList<>();
    }

    ResolvedResource manifestResource = resolvedManifest.get();
    debug(
        "[LOCAL_FALLBACK] manifest found. source='{}', readableDescription='{}'",
        manifestResource.sourceLabel,
        safeResourceDescription(manifestResource.resource)
    );

    try (InputStream inputStream = manifestResource.resource.getInputStream()) {
      byte[] manifestBytes = inputStream.readAllBytes();
      String manifestText = new String(manifestBytes, StandardCharsets.UTF_8);

      debug(
          "[LOCAL_FALLBACK] manifest bytes read. source='{}', byteLength={}, firstCharacters='{}'",
          manifestResource.sourceLabel,
          manifestBytes.length,
          previewText(manifestText, 240)
      );

      JsonNode root = objectMapper.readTree(manifestText);
      List<String> topLevelFields = jsonFieldNames(root);
      int manifestBucketCount = root.path("bucket_count").asInt(-1);
      JsonNode bucketsNode = root.path("buckets");

      if (!bucketsNode.isArray()) {
        lastManifestDiagnostic = "Manifest found at " + manifestResource.sourceLabel
            + " but JSON field 'buckets' is missing or not an array. topLevelFields=" + topLevelFields
            + ", bucket_count=" + manifestBucketCount;
        debug("[LOCAL_FALLBACK] manifest parsed but invalid shape. {}", lastManifestDiagnostic);
        return new ArrayList<>();
      }

      List<BucketRange> loadedBuckets = new ArrayList<>();
      int skippedBuckets = 0;
      int arraySize = bucketsNode.size();

      for (JsonNode bucketNode : bucketsNode) {
        int bucketId = bucketNode.path("bucket_id").asInt(-1);
        String filename = blankToNull(bucketNode.path("filename").asText(null));
        String minCodeText = blankToNull(bucketNode.path("min_code").asText(null));
        String maxCodeText = blankToNull(bucketNode.path("max_code").asText(null));
        int rowCount = bucketNode.path("row_count").asInt(0);

        BigInteger minCode = toBigIntegerOrNull(minCodeText);
        BigInteger maxCode = toBigIntegerOrNull(maxCodeText);

        if (filename == null || minCode == null || maxCode == null) {
          skippedBuckets++;
          if (skippedBuckets <= 8) {
            debug(
                "[LOCAL_FALLBACK] skipping invalid manifest bucket entry. rawNode={}, parsedBucketId={}, filename='{}', min_code='{}', max_code='{}', row_count={}",
                bucketNode.toString(),
                bucketId,
                filename,
                minCodeText,
                maxCodeText,
                rowCount
            );
          }
          continue;
        }

        loadedBuckets.add(new BucketRange(bucketId, filename, minCode, maxCode, rowCount));
      }

      loadedBuckets.sort((left, right) -> left.minCode.compareTo(right.minCode));

      lastManifestDiagnostic = "Manifest found at " + manifestResource.sourceLabel
          + "; bytes=" + manifestBytes.length
          + "; topLevelFields=" + topLevelFields
          + "; manifest.bucket_count=" + manifestBucketCount
          + "; bucketsArraySize=" + arraySize
          + "; validBucketsLoaded=" + loadedBuckets.size()
          + "; skippedBuckets=" + skippedBuckets
          + "; firstRange=" + (loadedBuckets.isEmpty() ? "n/a" : describeBucket(loadedBuckets.get(0)))
          + "; lastRange=" + (loadedBuckets.isEmpty() ? "n/a" : describeBucket(loadedBuckets.get(loadedBuckets.size() - 1)));

      debug("[LOCAL_FALLBACK] manifest load result. {}", lastManifestDiagnostic);

      if (manifestBucketCount != -1 && manifestBucketCount != loadedBuckets.size()) {
        debug(
            "[LOCAL_FALLBACK] manifest bucket_count does not equal valid loaded buckets. manifest.bucket_count={}, validBucketsLoaded={}, skippedBuckets={}",
            manifestBucketCount,
            loadedBuckets.size(),
            skippedBuckets
        );
      }

      return loadedBuckets;
    }
    catch (Exception e) {
      lastManifestDiagnostic = "Exception while loading/parsing manifest from " + manifestResource.sourceLabel
          + " description=" + safeResourceDescription(manifestResource.resource)
          + ": " + e.getClass().getSimpleName() + " - " + e.getMessage();
      debug("[LOCAL_FALLBACK] failed to load/parse manifest. {}", lastManifestDiagnostic, e);
      return new ArrayList<>();
    }
  }

  private BucketRange findBucket(BigInteger targetCode, List<BucketRange> loadedBuckets) {
    int low = 0;
    int high = loadedBuckets.size() - 1;
    int step = 0;

    while (low <= high) {
      int mid = (low + high) >>> 1;
      BucketRange bucket = loadedBuckets.get(mid);
      step++;

      debug(
          "[LOCAL_FALLBACK] bucket binary search step {}: low={}, high={}, mid={}, bucket={}",
          step,
          low,
          high,
          mid,
          describeBucket(bucket)
      );

      if (targetCode.compareTo(bucket.minCode) < 0) {
        high = mid - 1;
      }
      else if (targetCode.compareTo(bucket.maxCode) > 0) {
        low = mid + 1;
      }
      else {
        return bucket;
      }
    }

    return null;
  }

  private Optional<ProductDTO> scanBucket(BucketRange bucket, BigInteger targetCode, String fallbackBarcode) {
    String bucketPath = BUCKETS_PATH + bucket.filename;
    debug(
        "[LOCAL_FALLBACK] preparing to scan bucket. bucketId={}, filename='{}', preferredClasspathPath='{}'",
        bucket.bucketId,
        bucket.filename,
        bucketPath
    );

    Optional<ResolvedResource> resolvedBucket = resolveResource(bucketPath);
    if (resolvedBucket.isEmpty()) {
      debug(
          "[LOCAL_FALLBACK] selected bucket file was not found. bucketId={}, filename='{}', expectedPath='{}'. currentWorkingDirectory='{}'",
          bucket.bucketId,
          bucket.filename,
          bucketPath,
          Paths.get("").toAbsolutePath()
      );
      return Optional.empty();
    }

    ResolvedResource bucketResource = resolvedBucket.get();
    debug(
        "[LOCAL_FALLBACK] bucket file found. bucketId={}, source='{}', readableDescription='{}'",
        bucket.bucketId,
        bucketResource.sourceLabel,
        safeResourceDescription(bucketResource.resource)
    );

    int dataRowsScanned = 0;
    int rowsWithoutBarcode = 0;
    int rowsWithUnparseableBarcode = 0;
    String firstSeenCode = null;
    String lastSeenCode = null;
    String earlyStopCode = null;

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(bucketResource.resource.getInputStream(), StandardCharsets.UTF_8))) {
      String headerRecord = readNextCsvRecord(reader);
      if (headerRecord == null) {
        debug("[LOCAL_FALLBACK] bucket file is empty. bucketId={}, path='{}'", bucket.bucketId, bucketPath);
        return Optional.empty();
      }

      char delimiter = detectDelimiter(headerRecord);
      List<String> headers = cleanHeaders(parseDelimitedRecord(headerRecord, delimiter));
      String barcodeHeader = findFirstExistingHeader(headers, "code", "barcode", "_id", "id");

      debug(
          "[LOCAL_FALLBACK] parsed bucket header. bucketId={}, delimiter='{}', headerCount={}, barcodeHeader='{}', firstHeaders={}",
          bucket.bucketId,
          printableDelimiter(delimiter),
          headers.size(),
          barcodeHeader,
          previewList(headers, 16)
      );

      if (barcodeHeader == null) {
        debug(
            "[LOCAL_FALLBACK] bucket header does not contain any known barcode column. Expected one of [code, barcode, _id, id]. bucketId={}, headers={}",
            bucket.bucketId,
            previewList(headers, 80)
        );
      }

      String record;
      while ((record = readNextCsvRecord(reader)) != null) {
        dataRowsScanned++;
        Map<String, String> row = toRowMap(headers, parseDelimitedRecord(record, delimiter));
        String rowCode = normalizeBarcode(firstNonBlank(
            row.get("code"),
            row.get("barcode"),
            row.get("_id"),
            row.get("id")
        ));

        if (rowCode == null) {
          rowsWithoutBarcode++;
          if (rowsWithoutBarcode <= 3) {
            debug(
                "[LOCAL_FALLBACK] row without barcode. bucketId={}, rowNumberAfterHeader={}, knownColumnsPreview={}",
                bucket.bucketId,
                dataRowsScanned,
                previewMap(row, 8)
            );
          }
          continue;
        }

        BigInteger rowCodeNumber = toBigIntegerOrNull(rowCode);
        if (rowCodeNumber == null) {
          rowsWithUnparseableBarcode++;
          if (rowsWithUnparseableBarcode <= 3) {
            debug(
                "[LOCAL_FALLBACK] row barcode could not be parsed. bucketId={}, rowNumberAfterHeader={}, rawCode='{}'",
                bucket.bucketId,
                dataRowsScanned,
                rowCode
            );
          }
          continue;
        }

        if (firstSeenCode == null) {
          firstSeenCode = rowCode;
        }
        lastSeenCode = rowCode;

        if (dataRowsScanned <= 5 || dataRowsScanned % 10000 == 0) {
          debug(
              "[LOCAL_FALLBACK] scanning progress. bucketId={}, rowsScanned={}, currentRowBarcode='{}', target='{}'",
              bucket.bucketId,
              dataRowsScanned,
              rowCode,
              fallbackBarcode
          );
        }

        int comparison = rowCodeNumber.compareTo(targetCode);
        if (comparison == 0) {
          debug(
              "[LOCAL_FALLBACK] HIT. barcode='{}' found in bucketId={}, filename='{}', rowNumberAfterHeader={}, rowsScanned={}, source='{}'",
              fallbackBarcode,
              bucket.bucketId,
              bucket.filename,
              dataRowsScanned,
              dataRowsScanned,
              bucketResource.sourceLabel
          );

          ProductDTO dto = mapCsvRowToProduct(row, fallbackBarcode);
          Map<String, Object> rawProduct = dto.getRawProduct();
          if (rawProduct == null) {
            rawProduct = new LinkedHashMap<>();
          }
          rawProduct.put("_localFallback", true);
          rawProduct.put("_localBucketId", bucket.bucketId);
          rawProduct.put("_localBucketFilename", bucket.filename);
          rawProduct.put("_localBucketSource", bucketResource.sourceLabel);
          rawProduct.put("_localRowsScannedBeforeHit", dataRowsScanned);
          dto.setRawProduct(rawProduct);
          return Optional.of(dto);
        }

        // The bucket files are expected to be sorted by barcode in ascending numeric order.
        // Once we passed the requested barcode, the product cannot appear later in this bucket.
        if (comparison > 0) {
          earlyStopCode = rowCode;
          debug(
              "[LOCAL_FALLBACK] early stop. Current row barcode '{}' is numerically greater than target '{}'. bucketId={}, rowsScanned={}",
              rowCode,
              fallbackBarcode,
              bucket.bucketId,
              dataRowsScanned
          );
          break;
        }
      }
    }
    catch (Exception e) {
      debug(
          "[LOCAL_FALLBACK] exception while scanning bucket. bucketId={}, filename='{}', source='{}', rowsScannedBeforeException={}",
          bucket.bucketId,
          bucket.filename,
          bucketResource.sourceLabel,
          dataRowsScanned,
          e
      );
      return Optional.empty();
    }

    debug(
        "[LOCAL_FALLBACK] MISS. barcode='{}' was not found in selected bucket. bucketId={}, filename='{}', rowsScanned={}, rowsWithoutBarcode={}, rowsWithUnparseableBarcode={}, firstSeenCode='{}', lastSeenCode='{}', earlyStopCode='{}', source='{}'",
        fallbackBarcode,
        bucket.bucketId,
        bucket.filename,
        dataRowsScanned,
        rowsWithoutBarcode,
        rowsWithUnparseableBarcode,
        firstSeenCode,
        lastSeenCode,
        earlyStopCode,
        bucketResource.sourceLabel
    );

    return Optional.empty();
  }

  private Optional<ResolvedResource> resolveResource(String relativePath) {
    List<ResolvedResource> candidates = new ArrayList<>();
    candidates.add(new ResolvedResource("classpath:" + relativePath, new ClassPathResource(relativePath)));
    candidates.add(new ResolvedResource("filesystem:" + Paths.get(relativePath).toAbsolutePath(), new FileSystemResource(Paths.get(relativePath))));
    candidates.add(new ResolvedResource("filesystem:" + Paths.get("src/main/resources").resolve(relativePath).toAbsolutePath(), new FileSystemResource(Paths.get("src/main/resources").resolve(relativePath))));
    candidates.add(new ResolvedResource("filesystem:" + Paths.get("resources").resolve(relativePath).toAbsolutePath(), new FileSystemResource(Paths.get("resources").resolve(relativePath))));

    for (ResolvedResource candidate : candidates) {
      boolean exists = resourceExists(candidate.resource);
      debug("[LOCAL_FALLBACK] resource candidate check: source='{}', exists={}", candidate.sourceLabel, exists);
      if (exists) {
        return Optional.of(candidate);
      }
    }

    return Optional.empty();
  }

  private boolean resourceExists(Resource resource) {
    try {
      return resource.exists() && resource.isReadable();
    }
    catch (Exception e) {
      return false;
    }
  }

  private String safeResourceDescription(Resource resource) {
    try {
      return resource.getURI().toString();
    }
    catch (Exception ignored) {
      return resource.getDescription();
    }
  }

  private ProductDTO mapCsvRowToProduct(Map<String, String> row, String fallbackBarcode) {
    ProductDTO dto = new ProductDTO();

    String barcode = firstNonBlank(
        row.get("code"),
        row.get("barcode"),
        fallbackBarcode
    );
    dto.setBarcode(barcode);

    dto.setName(firstNonBlank(
        row.get("product_name"),
        row.get("product_name_en"),
        row.get("product_name_fr"),
        row.get("product_name_de"),
        row.get("product_name_it"),
        row.get("abbreviated_product_name"),
        row.get("generic_name"),
        "Unknown product"
    ));

    dto.setBrand(blankToNull(row.get("brands")));
    dto.setQuantity(firstNonBlank(
        row.get("quantity"),
        buildQuantityFromAmountAndUnit(row.get("product_quantity"), row.get("product_quantity_unit"))
    ));
    dto.setServingSize(firstNonBlank(
        row.get("serving_size"),
        buildQuantityFromAmountAndUnit(row.get("serving_quantity"), row.get("serving_quantity_unit"))
    ));

    dto.setImageUrl(firstNonBlank(
        row.get("image_front_url"),
        row.get("image_url"),
        row.get("selected_images.front.display.en"),
        row.get("selected_images.front.small.en"),
        buildOpenFoodFactsImageUrl(barcode, row)
    ));

    dto.setProductUrl(firstNonBlank(
        row.get("url"),
        buildOpenFoodFactsProductUrl(barcode)
    ));
    dto.setNutriScore(firstNonBlank(
        row.get("nutrition_grades"),
        row.get("nutriscore_grade"),
        row.get("nutri_score")
    ));

    dto.setStores(extractList(row.get("stores")));
    dto.setStoreTags(extractList(row.get("stores_tags")));
    dto.setPurchasePlaces(extractList(firstNonBlank(
        row.get("purchase_places"),
        row.get("purchase_places_tags")
    )));

    Map<String, Object> nutriments = firstNonNullMap(
        parseJsonObject(row.get("nutriments")),
        extractNutrimentsFromFlatColumns(row)
    );
    dto.setNutriments(nutriments);
    dto.setNutriScoreData(parseJsonObject(row.get("nutriscore_data")));
    dto.setRawProduct(rawRow(row));
    dto.setLocalFallback(true);
    dto.setDataSource("local_csv_fallback");
    dto.setCaloriesPerPackage(computeCaloriesPerPackage(row, dto.getQuantity(), nutriments));

    return dto;
  }

  private Map<String, Object> extractNutrimentsFromFlatColumns(Map<String, String> row) {
    Map<String, Object> nutriments = new LinkedHashMap<>();

    for (Map.Entry<String, String> entry : row.entrySet()) {
      String key = entry.getKey();
      String value = blankToNull(entry.getValue());

      if (value == null) {
        continue;
      }

      if (isLikelyNutrimentColumn(key)) {
        nutriments.put(key, parseNumberIfPossible(value));
      }
    }

    addCompactEnergyColumns(row, nutriments);

    return nutriments.isEmpty() ? null : nutriments;
  }

  private boolean isLikelyNutrimentColumn(String key) {
    if (key == null) {
      return false;
    }

    return key.endsWith("_100g")
        || key.endsWith("-100g")
        || key.endsWith("_serving")
        || key.endsWith("-serving")
        || key.equals("energy-kcal_100g")
        || key.equals("energy_100g")
        || key.equals("energy_kcal_value")
        || key.equals("energy_kcal_basis")
        || key.equals("energy_kcal_unit")
        || key.equals("fat_100g")
        || key.equals("carbohydrates_100g")
        || key.equals("proteins_100g")
        || key.equals("sugars_100g")
        || key.equals("salt_100g");
  }

  private void addCompactEnergyColumns(Map<String, String> row, Map<String, Object> nutriments) {
    Double energyKcalValue = parseDoubleOrNull(row.get("energy_kcal_value"));
    if (energyKcalValue == null) {
      return;
    }

    String basis = blankToNull(row.get("energy_kcal_basis"));
    String normalizedBasis = basis == null ? "" : basis.toLowerCase(Locale.ROOT).replace(" ", "");

    nutriments.put("energy-kcal_value", roundTwoDecimals(energyKcalValue));
    if (basis != null) {
      nutriments.put("energy-kcal_basis", basis);
    }
    String unit = blankToNull(row.get("energy_kcal_unit"));
    if (unit != null) {
      nutriments.put("energy-kcal_unit", unit);
    }

    if (normalizedBasis.equals("100g")) {
      nutriments.put("energy-kcal_100g", roundTwoDecimals(energyKcalValue));
    }
    else if (normalizedBasis.equals("100ml")) {
      nutriments.put("energy-kcal_100ml", roundTwoDecimals(energyKcalValue));
    }
    else if (normalizedBasis.equals("serving") || normalizedBasis.equals("perserving")) {
      nutriments.put("energy-kcal_serving", roundTwoDecimals(energyKcalValue));
    }
  }

  private String buildQuantityFromAmountAndUnit(String amountText, String unitText) {
    String amount = blankToNull(amountText);
    String unit = blankToNull(unitText);

    if (amount == null || unit == null) {
      return null;
    }

    return amount + " " + unit;
  }

  private String buildOpenFoodFactsProductUrl(String barcode) {
    String normalizedBarcode = normalizeBarcode(barcode);
    if (normalizedBarcode == null) {
      return null;
    }

    return "https://world.openfoodfacts.org/product/" + normalizedBarcode;
  }

  private String buildOpenFoodFactsImageUrl(String barcode, Map<String, String> row) {
    String normalizedBarcode = normalizeBarcode(barcode);
    if (normalizedBarcode == null) {
      return null;
    }

    String folder = buildOpenFoodFactsImageFolder(normalizedBarcode);
    if (folder == null) {
      return null;
    }

    String selectedFrontImageUrl = buildSelectedFrontImageUrl(folder, row.get("image_1"));
    if (selectedFrontImageUrl != null) {
      return selectedFrontImageUrl;
    }

    selectedFrontImageUrl = buildSelectedFrontImageUrl(folder, row.get("selected_images_front"));
    if (selectedFrontImageUrl != null) {
      return selectedFrontImageUrl;
    }

    String rawImageUrl = buildRawImageUrl(folder, row.get("image"));
    if (rawImageUrl != null) {
      return rawImageUrl;
    }

    return buildRawImageUrl(folder, row.get("image_1_id"));
  }

  private String buildOpenFoodFactsImageFolder(String barcode) {
    String code = normalizeBarcode(barcode);
    if (code == null) {
      return null;
    }

    if (code.length() < 13) {
      code = "0".repeat(13 - code.length()) + code;
    }

    if (code.length() <= 8) {
      return code;
    }

    return code.substring(0, 3)
        + "/" + code.substring(3, 6)
        + "/" + code.substring(6, 9)
        + "/" + code.substring(9);
  }

  private String buildSelectedFrontImageUrl(String folder, String imageMapText) {
    Map<String, Object> imageMap = parseJsonObject(imageMapText);
    if (imageMap == null || imageMap.isEmpty()) {
      return null;
    }

    String language = chooseImageLanguage(imageMap);
    if (language == null) {
      return null;
    }

    String revision = blankToNull(String.valueOf(imageMap.get(language)));
    if (revision == null) {
      return null;
    }

    return "https://images.openfoodfacts.org/images/products/"
        + folder
        + "/front_"
        + language.toLowerCase(Locale.ROOT).trim()
        + "."
        + revision
        + ".400.jpg";
  }

  private String buildRawImageUrl(String folder, String rawImageIdText) {
    String rawImageId = blankToNull(rawImageIdText);
    if (rawImageId == null) {
      return null;
    }

    String digitsOnly = normalizeBarcode(rawImageId);
    if (digitsOnly == null || !digitsOnly.equals(rawImageId.trim())) {
      return null;
    }

    return "https://images.openfoodfacts.org/images/products/" + folder + "/" + digitsOnly + ".400.jpg";
  }

  private String chooseImageLanguage(Map<String, Object> imageMap) {
    String[] preferredLanguages = new String[] { "en", "fr", "de", "it", "es" };
    for (String preferredLanguage : preferredLanguages) {
      if (imageMap.containsKey(preferredLanguage) && blankToNull(String.valueOf(imageMap.get(preferredLanguage))) != null) {
        return preferredLanguage;
      }
    }

    for (Map.Entry<String, Object> entry : imageMap.entrySet()) {
      String language = blankToNull(entry.getKey());
      String revision = blankToNull(String.valueOf(entry.getValue()));
      if (language != null && revision != null) {
        return language;
      }
    }

    return null;
  }

  private Double computeCaloriesPerPackage(Map<String, String> row, String quantityText, Map<String, Object> nutriments) {
    Double compactEnergyValue = parseDoubleOrNull(row.get("energy_kcal_value"));
    String compactBasis = blankToNull(row.get("energy_kcal_basis"));

    if (compactEnergyValue != null && compactBasis != null) {
      Double fromCompactColumns = computeCaloriesFromEnergyAndPackageAmount(
          compactEnergyValue,
          compactBasis,
          firstNonBlank(row.get("product_quantity"), extractQuantityAmount(quantityText)),
          firstNonBlank(row.get("product_quantity_unit"), extractQuantityUnit(quantityText))
      );
      if (fromCompactColumns != null) {
        return fromCompactColumns;
      }
    }

    Double kcal100g = parseDoubleFromMap(nutriments, "energy-kcal_100g", "energy_kcal_100g");
    if (kcal100g != null) {
      Double calories = computeCaloriesFromEnergyAndPackageAmount(
          kcal100g,
          "100g",
          firstNonBlank(row.get("product_quantity"), extractQuantityAmount(quantityText)),
          firstNonBlank(row.get("product_quantity_unit"), extractQuantityUnit(quantityText))
      );
      if (calories != null) {
        return calories;
      }
    }

    Double kcal100ml = parseDoubleFromMap(nutriments, "energy-kcal_100ml", "energy_kcal_100ml");
    if (kcal100ml != null) {
      Double calories = computeCaloriesFromEnergyAndPackageAmount(
          kcal100ml,
          "100ml",
          firstNonBlank(row.get("product_quantity"), extractQuantityAmount(quantityText)),
          firstNonBlank(row.get("product_quantity_unit"), extractQuantityUnit(quantityText))
      );
      if (calories != null) {
        return calories;
      }
    }

    Double kcalServing = parseDoubleFromMap(nutriments, "energy-kcal_serving", "energy_kcal_serving");
    return kcalServing == null ? null : roundTwoDecimals(kcalServing);
  }

  private Double computeCaloriesFromEnergyAndPackageAmount(Double kcalValue, String basisText, String amountText, String unitText) {
    if (kcalValue == null) {
      return null;
    }

    String basis = blankToNull(basisText);
    Double amount = parseDoubleOrNull(amountText);
    String unit = normalizeUnit(unitText);

    if (basis == null || amount == null || unit == null) {
      return null;
    }

    String normalizedBasis = basis.toLowerCase(Locale.ROOT).replace(" ", "");
    Double amountInBasisUnit = convertAmountToBasisUnit(amount, unit, normalizedBasis);

    if (amountInBasisUnit == null) {
      return null;
    }

    return roundTwoDecimals((kcalValue * amountInBasisUnit) / 100.0);
  }

  private Double convertAmountToBasisUnit(Double amount, String unit, String normalizedBasis) {
    if (amount == null || unit == null || normalizedBasis == null) {
      return null;
    }

    if (normalizedBasis.equals("100g")) {
      if (unit.equals("g")) {
        return amount;
      }
      if (unit.equals("kg")) {
        return amount * 1000.0;
      }
      if (unit.equals("mg")) {
        return amount / 1000.0;
      }
      return null;
    }

    if (normalizedBasis.equals("100ml")) {
      if (unit.equals("ml")) {
        return amount;
      }
      if (unit.equals("l")) {
        return amount * 1000.0;
      }
      if (unit.equals("cl")) {
        return amount * 10.0;
      }
      return null;
    }

    return null;
  }

  private String extractQuantityAmount(String quantityText) {
    String text = blankToNull(quantityText);
    if (text == null) {
      return null;
    }

    NumberToken number = findNumberToken(text, 0);
    return number == null ? null : number.rawValue;
  }

  private String extractQuantityUnit(String quantityText) {
    String text = blankToNull(quantityText);
    if (text == null) {
      return null;
    }

    UnitToken unit = findUnitToken(text);
    return unit == null ? null : unit.value;
  }

  private NumberToken findNumberToken(String text, int startIndex) {
    int index = Math.max(0, startIndex);
    while (index < text.length() && !Character.isDigit(text.charAt(index))) {
      index++;
    }
    if (index >= text.length()) {
      return null;
    }

    int cursor = index;
    boolean hasDecimalSeparator = false;
    while (cursor < text.length()) {
      char current = text.charAt(cursor);
      if (Character.isDigit(current)) {
        cursor++;
        continue;
      }
      if ((current == '.' || current == ',') && !hasDecimalSeparator) {
        hasDecimalSeparator = true;
        cursor++;
        continue;
      }
      break;
    }

    return new NumberToken(text.substring(index, cursor));
  }

  private UnitToken findUnitToken(String text) {
    String normalized = text.toLowerCase(Locale.ROOT);
    String[] units = {"kg", "mg", "ml", "cl", "g", "l"};
    for (int index = 0; index < normalized.length(); index++) {
      for (String unit : units) {
        int endIndex = index + unit.length();
        if (endIndex <= normalized.length()
            && normalized.startsWith(unit, index)
            && isUnitBoundary(normalized, endIndex)) {
          return new UnitToken(text.substring(index, endIndex));
        }
      }
    }
    return null;
  }

  private boolean isUnitBoundary(String text, int index) {
    return index >= text.length() || !Character.isLetter(text.charAt(index));
  }

  private static class NumberToken {
    private final String rawValue;

    private NumberToken(String rawValue) {
      this.rawValue = rawValue;
    }
  }

  private static class UnitToken {
    private final String value;

    private UnitToken(String value) {
      this.value = value;
    }
  }

  private String normalizeUnit(String unitText) {
    String unit = blankToNull(unitText);
    if (unit == null) {
      return null;
    }

    return unit.toLowerCase(Locale.ROOT).replace(".", "").trim();
  }

  private Double parseDoubleFromMap(Map<String, Object> map, String... keys) {
    if (map == null) {
      return null;
    }

    for (String key : keys) {
      Object value = map.get(key);
      Double parsed = parseDoubleOrNull(value);
      if (parsed != null) {
        return parsed;
      }
    }

    return null;
  }

  private Double parseDoubleOrNull(Object value) {
    if (value == null) {
      return null;
    }

    if (value instanceof Number number) {
      return number.doubleValue();
    }

    String text = blankToNull(String.valueOf(value));
    if (text == null) {
      return null;
    }

    try {
      return Double.parseDouble(text.replace(',', '.'));
    }
    catch (NumberFormatException ignored) {
      return null;
    }
  }

  private Double roundTwoDecimals(Double value) {
    if (value == null) {
      return null;
    }

    return Math.round(value * 100.0) / 100.0;
  }

  private Object parseNumberIfPossible(String value) {
    try {
      return Double.parseDouble(value);
    }
    catch (NumberFormatException ignored) {
      return value;
    }
  }

  private List<String> extractList(String value) {
    String text = blankToNull(value);
    if (text == null) {
      return new ArrayList<>();
    }

    List<String> jsonList = parseJsonStringList(text);
    if (jsonList != null) {
      return jsonList;
    }

    Set<String> values = new LinkedHashSet<>();
    for (String piece : text.split(",")) {
      String cleaned = blankToNull(piece);
      if (cleaned != null) {
        values.add(cleaned);
      }
    }

    return new ArrayList<>(values);
  }

  private List<String> parseJsonStringList(String text) {
    if (!text.startsWith("[")) {
      return null;
    }

    try {
      List<Object> rawValues = objectMapper.readValue(text, new TypeReference<List<Object>>() {});
      List<String> values = new ArrayList<>();
      for (Object rawValue : rawValues) {
        String cleaned = blankToNull(String.valueOf(rawValue));
        if (cleaned != null) {
          values.add(cleaned);
        }
      }
      return values;
    }
    catch (Exception ignored) {
      return null;
    }
  }

  private Map<String, Object> parseJsonObject(String value) {
    String text = blankToNull(value);
    if (text == null || !text.startsWith("{")) {
      return null;
    }

    try {
      return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {});
    }
    catch (Exception ignored) {
      return null;
    }
  }

  private Map<String, Object> rawRow(Map<String, String> row) {
    Map<String, Object> raw = new LinkedHashMap<>();

    for (Map.Entry<String, String> entry : row.entrySet()) {
      raw.put(entry.getKey(), blankToNull(entry.getValue()));
    }

    return raw;
  }

  private Map<String, String> toRowMap(List<String> headers, List<String> values) {
    Map<String, String> row = new LinkedHashMap<>();

    for (int i = 0; i < headers.size(); i++) {
      String header = headers.get(i);
      String value = i < values.size() ? values.get(i) : null;
      row.put(header, value);
    }

    return row;
  }

  private List<String> cleanHeaders(List<String> rawHeaders) {
    List<String> cleanedHeaders = new ArrayList<>();
    for (String rawHeader : rawHeaders) {
      String cleaned = rawHeader == null ? "" : rawHeader.replace("\uFEFF", "").trim();
      cleanedHeaders.add(cleaned);
    }
    return cleanedHeaders;
  }

  private String findFirstExistingHeader(List<String> headers, String... candidates) {
    for (String candidate : candidates) {
      if (headers.contains(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  private char detectDelimiter(String headerRecord) {
    char[] candidates = new char[] { ',', '\t', ';' };
    char bestDelimiter = ',';
    int bestCount = -1;

    for (char candidate : candidates) {
      int count = countDelimiterOutsideQuotes(headerRecord, candidate);
      if (count > bestCount) {
        bestCount = count;
        bestDelimiter = candidate;
      }
    }

    return bestDelimiter;
  }

  private int countDelimiterOutsideQuotes(String text, char delimiter) {
    int count = 0;
    boolean inQuotes = false;

    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);

      if (c == '"') {
        if (inQuotes && i + 1 < text.length() && text.charAt(i + 1) == '"') {
          i++;
        }
        else {
          inQuotes = !inQuotes;
        }
      }
      else if (c == delimiter && !inQuotes) {
        count++;
      }
    }

    return count;
  }

  private String readNextCsvRecord(BufferedReader reader) throws java.io.IOException {
    String line = reader.readLine();
    if (line == null) {
      return null;
    }

    StringBuilder record = new StringBuilder(line);
    while (!hasBalancedQuotes(record)) {
      String nextLine = reader.readLine();
      if (nextLine == null) {
        break;
      }
      record.append('\n').append(nextLine);
    }

    return record.toString();
  }

  private boolean hasBalancedQuotes(CharSequence text) {
    boolean inQuotes = false;

    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c != '"') {
        continue;
      }

      if (inQuotes && i + 1 < text.length() && text.charAt(i + 1) == '"') {
        i++;
      }
      else {
        inQuotes = !inQuotes;
      }
    }

    return !inQuotes;
  }

  private List<String> parseDelimitedRecord(String record, char delimiter) {
    List<String> values = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;

    for (int i = 0; i < record.length(); i++) {
      char c = record.charAt(i);

      if (c == '"') {
        if (inQuotes && i + 1 < record.length() && record.charAt(i + 1) == '"') {
          current.append('"');
          i++;
        }
        else {
          inQuotes = !inQuotes;
        }
      }
      else if (c == delimiter && !inQuotes) {
        values.add(current.toString());
        current.setLength(0);
      }
      else {
        current.append(c);
      }
    }

    values.add(current.toString());
    return values;
  }

  private String normalizeBarcode(String value) {
    String text = blankToNull(value);
    if (text == null) {
      return null;
    }

    StringBuilder digits = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isDigit(c)) {
        digits.append(c);
      }
    }

    return digits.length() == 0 ? null : digits.toString();
  }

  private BigInteger toBigIntegerOrNull(String value) {
    String normalized = normalizeBarcode(value);
    if (normalized == null) {
      return null;
    }

    try {
      return new BigInteger(normalized);
    }
    catch (NumberFormatException ignored) {
      return null;
    }
  }

  private String describeBucket(BucketRange bucket) {
    if (bucket == null) {
      return "n/a";
    }
    return "bucketId=" + bucket.bucketId
        + ", filename='" + bucket.filename + "'"
        + ", range=" + bucket.minCode + ".." + bucket.maxCode
        + ", rowCount=" + bucket.rowCount;
  }

  private String describeNearestBuckets(BigInteger targetCode, List<BucketRange> loadedBuckets) {
    BucketRange previous = null;
    BucketRange next = null;

    for (BucketRange bucket : loadedBuckets) {
      if (bucket.maxCode.compareTo(targetCode) < 0) {
        previous = bucket;
      }
      else if (bucket.minCode.compareTo(targetCode) > 0) {
        next = bucket;
        break;
      }
    }

    return "previous={" + describeBucket(previous) + "}, next={" + describeBucket(next) + "}";
  }

  private String printableDelimiter(char delimiter) {
    if (delimiter == '\t') {
      return "TAB";
    }
    return String.valueOf(delimiter);
  }

  private String previewList(List<String> values, int maxItems) {
    if (values == null) {
      return "null";
    }
    List<String> preview = new ArrayList<>();
    for (int i = 0; i < values.size() && i < maxItems; i++) {
      preview.add(values.get(i));
    }
    if (values.size() > maxItems) {
      preview.add("... +" + (values.size() - maxItems) + " more");
    }
    return preview.toString();
  }

  private String previewMap(Map<String, String> values, int maxItems) {
    if (values == null) {
      return "null";
    }
    Map<String, String> preview = new LinkedHashMap<>();
    int count = 0;
    for (Map.Entry<String, String> entry : values.entrySet()) {
      if (count >= maxItems) {
        preview.put("...", "+" + (values.size() - maxItems) + " more");
        break;
      }
      preview.put(entry.getKey(), entry.getValue());
      count++;
    }
    return preview.toString();
  }

  private Map<String, Object> firstNonNullMap(Map<String, Object>... candidates) {
    for (Map<String, Object> candidate : candidates) {
      if (candidate != null) {
        return candidate;
      }
    }
    return null;
  }

  private String firstNonBlank(String... candidates) {
    for (String candidate : candidates) {
      String value = blankToNull(candidate);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private List<String> jsonFieldNames(JsonNode node) {
    List<String> names = new ArrayList<>();
    if (node == null || !node.isObject()) {
      return names;
    }

    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private String previewText(String text, int maxLength) {
    if (text == null) {
      return "null";
    }

    String normalized = text.replace("\r", "\\r").replace("\n", "\\n");
    if (normalized.length() <= maxLength) {
      return normalized;
    }
    return normalized.substring(0, maxLength) + "...";
  }


  private static class ManifestFile {
    public int bucket_count;
    public List<BucketFile> buckets = new ArrayList<>();
  }

  private static class BucketFile {
    public int bucket_id;
    public String filename;
    public String min_code;
    public String max_code;
    public int row_count;
  }

  private static class BucketRange {
    private final int bucketId;
    private final String filename;
    private final BigInteger minCode;
    private final BigInteger maxCode;
    private final int rowCount;

    private BucketRange(int bucketId, String filename, BigInteger minCode, BigInteger maxCode, int rowCount) {
      this.bucketId = bucketId;
      this.filename = filename;
      this.minCode = minCode;
      this.maxCode = maxCode;
      this.rowCount = rowCount;
    }
  }

  private static class ResolvedResource {
    private final String sourceLabel;
    private final Resource resource;

    private ResolvedResource(String sourceLabel, Resource resource) {
      this.sourceLabel = sourceLabel;
      this.resource = resource;
    }
  }

  private static void debug(String template, Object... args) {
    log.warn(template, args);
    System.err.println(formatForFallbackConsole(template, args));

    Throwable throwable = trailingThrowable(args);
    if (throwable != null) {
      throwable.printStackTrace(System.err);
    }
  }

  private static String formatForFallbackConsole(String template, Object... args) {
    StringBuilder result = new StringBuilder();
    result.append(java.time.LocalDateTime.now()).append(" ");

    int argLimit = args == null ? 0 : args.length;
    if (argLimit > 0 && args[argLimit - 1] instanceof Throwable) {
      argLimit--;
    }

    int argIndex = 0;
    for (int i = 0; i < template.length(); i++) {
      char current = template.charAt(i);
      if (current == '{' && i + 1 < template.length() && template.charAt(i + 1) == '}' && argIndex < argLimit) {
        result.append(String.valueOf(args[argIndex++]));
        i++;
      }
      else {
        result.append(current);
      }
    }

    if (argIndex < argLimit) {
      result.append(" | extraArgs=");
      for (int i = argIndex; i < argLimit; i++) {
        if (i > argIndex) {
          result.append(", ");
        }
        result.append(String.valueOf(args[i]));
      }
    }

    return result.toString();
  }

  private static Throwable trailingThrowable(Object... args) {
    if (args == null || args.length == 0) {
      return null;
    }

    Object lastArg = args[args.length - 1];
    return lastArg instanceof Throwable throwable ? throwable : null;
  }

}
