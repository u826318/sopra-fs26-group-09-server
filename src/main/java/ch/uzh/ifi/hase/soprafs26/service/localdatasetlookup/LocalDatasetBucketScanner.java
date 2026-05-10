package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
public class LocalDatasetBucketScanner {

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
        Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
        CSVParser parser = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .build()
            .parse(reader)
    ) {
      for (CSVRecord record : parser) {
        String rowBarcode = getFirstPresent(record, "code", "barcode", "_id", "id");

        if (barcode.equals(rowBarcode)) {
          return Optional.of(toMap(record));
        }
      }

      return Optional.empty();
    }
    catch (IOException e) {
      throw new IllegalStateException("Failed to read local dataset bucket file: " + bucketPath, e);
    }
  }

  private String getFirstPresent(CSVRecord record, String... columnNames) {
    for (String columnName : columnNames) {
      if (record.isMapped(columnName)) {
        String value = record.get(columnName);

        if (value != null && !value.isBlank()) {
          return value.trim();
        }
      }
    }

    return "";
  }

  private Map<String, String> toMap(CSVRecord record) {
    Map<String, String> row = new LinkedHashMap<>();

    for (String header : record.getParser().getHeaderMap().keySet()) {
      row.put(header, record.get(header));
    }

    return row;
  }
}