package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.io.BufferedReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Component
public class LocalDatasetBucketScanner {

  private static final Logger log = LoggerFactory.getLogger(LocalDatasetLookupService.class);

  private static final String BUCKETS_DIRECTORY = "local-dataset/buckets/";

  public Optional<Map<String, String>> findRowByBarcode(
      LocalDatasetBucket bucket,
      String barcode
  ) {
    Objects.requireNonNull(bucket, "bucket must not be null");
    Objects.requireNonNull(barcode, "barcode must not be null");

    String bucketPath = BUCKETS_DIRECTORY + bucket.filename();
    ClassPathResource resource = new ClassPathResource(bucketPath);

    if (!resource.exists()) {
      throw new IllegalStateException("Local dataset bucket file not found: " + bucketPath);
    }

    try (
        Reader reader = openUtf8ReaderWithoutBom(resource);
        CSVParser parser = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .build()
            .parse(reader)
    ) {
      for (CSVRecord record : parser) {
        String rowBarcode = record.get("code");
        
        if (barcode.equals(rowBarcode)) {
          log.info("\n barcode found: '{}' \n", barcode);
          return Optional.of(toMap(record));
        }
    }
    
      log.info("\n barcode not found: '{}' \n", barcode);
      return Optional.empty();
    }
    catch (IOException e) {
      throw new IllegalStateException("Failed to read local dataset bucket file: " + bucketPath, e);
    }
  }

  public Optional<Map<String, String>> findRowByProductIndex(
      LocalDatasetBucket bucket,
      Long productIndex
  ) {
    if (productIndex == null) {
      return Optional.empty();
    }

    Map<Long, Map<String, String>> rows = findRowsByProductIndices(bucket, Set.of(productIndex));
    return Optional.ofNullable(rows.get(productIndex));
  }

  public Map<Long, Map<String, String>> findRowsByProductIndices(
      LocalDatasetBucket bucket,
      Collection<Long> productIndices
  ) {
    Objects.requireNonNull(bucket, "bucket must not be null");

    Set<Long> remaining = new HashSet<>();
    if (productIndices != null) {
      for (Long productIndex : productIndices) {
        if (productIndex != null && bucket.containsProductIndex(productIndex)) {
          remaining.add(productIndex);
        }
      }
    }

    if (remaining.isEmpty()) {
      return Map.of();
    }

    return findRowsByProductIndexOffsets(bucket, remaining);
  }

  /**
   * Fast path for the indexed local dataset.
   *
   * The manifest says product_index_assignment = barcode_ascending_1_based, and each bucket
   * has a contiguous min_product_index/max_product_index range. That means a product index maps
   * directly to its 1-based row number inside the bucket:
   *
   *   rowNumberAfterHeader = productIndex - bucket.minProductIndex + 1
   *
   * This avoids parsing every CSV row with Apache Commons CSV. We still read sequentially, but we
   * only parse the exact target lines and stop once the largest needed row number has passed.
   */
  private Map<Long, Map<String, String>> findRowsByProductIndexOffsets(
      LocalDatasetBucket bucket,
      Set<Long> productIndices
  ) {
    String bucketPath = BUCKETS_DIRECTORY + bucket.filename();
    ClassPathResource resource = new ClassPathResource(bucketPath);

    if (!resource.exists()) {
      throw new IllegalStateException("Local dataset bucket file not found: " + bucketPath);
    }

    TreeMap<Integer, Long> rowNumberToProductIndex = new TreeMap<>();
    for (Long productIndex : productIndices) {
      long rowNumber = productIndex - bucket.minProductIndex() + 1;
      if (rowNumber > 0 && rowNumber <= bucket.rowCount() && rowNumber <= Integer.MAX_VALUE) {
        rowNumberToProductIndex.put((int) rowNumber, productIndex);
      }
    }

    if (rowNumberToProductIndex.isEmpty()) {
      return Map.of();
    }

    int maxTargetRowNumber = rowNumberToProductIndex.lastKey();
    Map<Long, Map<String, String>> rows = new LinkedHashMap<>();

    try (BufferedReader reader = new BufferedReader(openUtf8ReaderWithoutBom(resource))) {
      String headerLine = reader.readLine();
      if (headerLine == null) {
        return rows;
      }

      List<String> headers = parseCsvLine(headerLine, bucketPath).stream()
          .map(String::trim)
          .toList();

      String line;
      int rowNumberAfterHeader = 0;
      while ((line = reader.readLine()) != null) {
        rowNumberAfterHeader += 1;

        Long requestedProductIndex = rowNumberToProductIndex.get(rowNumberAfterHeader);
        if (requestedProductIndex != null) {
          rows.put(requestedProductIndex, parseCsvDataLine(headers, line, bucketPath));

          if (rows.size() == rowNumberToProductIndex.size()) {
            break;
          }
        }

        if (rowNumberAfterHeader >= maxTargetRowNumber) {
          break;
        }
      }

      return rows;
    }
    catch (IOException e) {
      throw new IllegalStateException("Failed to read local dataset bucket file: " + bucketPath, e);
    }
  }

  private Map<String, String> parseCsvDataLine(
      List<String> headers,
      String line,
      String bucketPath
  ) throws IOException {
    List<String> values = parseCsvLine(line, bucketPath);
    Map<String, String> row = new LinkedHashMap<>();

    for (int i = 0; i < headers.size(); i += 1) {
      String value = i < values.size() ? values.get(i) : "";
      row.put(headers.get(i), value);
    }

    return row;
  }

  private List<String> parseCsvLine(String line, String bucketPath) throws IOException {
    try (
        CSVParser parser = CSVFormat.DEFAULT.builder()
            .setTrim(true)
            .build()
            .parse(new StringReader(line))
    ) {
      java.util.Iterator<CSVRecord> it = parser.iterator();
      return it.hasNext() ? it.next().stream().toList() : List.of();
    }
    catch (IOException e) {
      throw new IOException("Failed to parse CSV line in local dataset bucket file: " + bucketPath, e);
    }
  }

  private Map<String, String> toMap(CSVRecord record) {
    Map<String, String> row = new LinkedHashMap<>();

    for (String header : record.getParser().getHeaderMap().keySet()) {
      row.put(header, record.get(header));
    }

    return row;
  }

  private Long parseLong(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    try {
      return Long.parseLong(value.trim());
    }
    catch (NumberFormatException ignored) {
      return null;
    }
  }

  private Reader openUtf8ReaderWithoutBom(ClassPathResource resource) throws IOException {
    BufferedReader reader = new BufferedReader(
        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
    );

    reader.mark(1);
    int firstCharacter = reader.read();

    if (firstCharacter != '\uFEFF' && firstCharacter != -1) {
        reader.reset();
    }

    return reader;
  }
}
