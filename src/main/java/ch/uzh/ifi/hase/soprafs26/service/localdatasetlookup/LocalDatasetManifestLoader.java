package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class LocalDatasetManifestLoader {
  private static final String MANIFEST_PATH = "local-dataset/manifest.json";

  private final ObjectMapper objectMapper = new ObjectMapper();

  public List<LocalDatasetBucket> loadBuckets() {
    ClassPathResource resource = new ClassPathResource(MANIFEST_PATH);

    if (!resource.exists()) {
      throw new IllegalStateException("Local dataset manifest not found at: " + MANIFEST_PATH);
    }

    try (InputStream inputStream = resource.getInputStream()) {
      JsonNode root = objectMapper.readTree(inputStream);
      JsonNode bucketsNode = root.path("buckets");

      List<LocalDatasetBucket> buckets = new ArrayList<>();

      for (JsonNode bucketNode : bucketsNode) {
        buckets.add(toBucket(bucketNode));
      }

      return buckets;
    }
    catch (IOException e) {
      throw new IllegalStateException("Failed to read local dataset manifest.", e);
    }
  }

  private LocalDatasetBucket toBucket(JsonNode bucketNode) {
    int bucketId = bucketNode.path("bucket_id").asInt();
    String filename = bucketNode.path("filename").asText(null);
    long rowCount = bucketNode.path("row_count").asLong();
    String minBarcode = bucketNode.path("min_code").asText(null);
    String maxBarcode = bucketNode.path("max_code").asText(null);

    return new LocalDatasetBucket(
        bucketId,
        filename,
        rowCount,
        minBarcode,
        maxBarcode
    );
  }
}
