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

  private Map<String, String> toMap(CSVRecord record) {
    Map<String, String> row = new LinkedHashMap<>();

    for (String header : record.getParser().getHeaderMap().keySet()) {
      row.put(header, record.get(header));
    }

    return row;
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