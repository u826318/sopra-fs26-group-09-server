package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CsvNameIndexManifestLoader {

  private static final Logger log = LoggerFactory.getLogger(CsvNameIndexManifestLoader.class);
  static final String NAME_INDEX_ROOT = "local-dataset/name-index";
  private static final String MANIFEST_RESOURCE = NAME_INDEX_ROOT + "/manifest.json";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private volatile CsvNameIndexManifest cachedManifest;

  public CsvNameIndexManifest loadManifest() throws IOException {
    CsvNameIndexManifest current = cachedManifest;
    if (current != null) {
      return current;
    }

    synchronized (this) {
      if (cachedManifest != null) {
        return cachedManifest;
      }

      ClassPathResource resource = new ClassPathResource(MANIFEST_RESOURCE);
      if (!resource.exists()) {
        throw new IOException("Missing classpath resource: " + MANIFEST_RESOURCE);
      }

      JsonNode root;
      try (java.io.InputStream inputStream = resource.getInputStream()) {
        root = objectMapper.readTree(inputStream);
      }
      CsvNameIndexManifest manifest = parseManifest(root);
      cachedManifest = manifest;
      log.info(
          "[NAME_INDEX_CSV] loaded manifest schema={} tokenShards={} metadataShards={} root={}",
          manifest.schemaVersion(),
          manifest.tokenPostings().shardCount(),
          manifest.productMetadata().shardCount(),
          NAME_INDEX_ROOT
      );
      return manifest;
    }
  }

  private CsvNameIndexManifest parseManifest(JsonNode root) throws IOException {
    String schemaVersion = requiredText(root, "schema_version");
    CsvNameIndexShardConfig tokenPostings = parseShardConfig(root.path("token_postings"), "token_postings");
    CsvNameIndexShardConfig productMetadata = parseShardConfig(root.path("product_metadata"), "product_metadata");

    if (!"name_index_csv_v1".equals(schemaVersion)) {
      throw new IOException("Unsupported name-index manifest schema_version: " + schemaVersion);
    }

    return new CsvNameIndexManifest(schemaVersion, tokenPostings, productMetadata);
  }

  private CsvNameIndexShardConfig parseShardConfig(JsonNode node, String sectionName) throws IOException {
    if (node == null || node.isMissingNode() || !node.isObject()) {
      throw new IOException("Missing or invalid manifest section: " + sectionName);
    }

    String directory = requiredText(node, "directory");
    int shardCount = requiredPositiveInt(node, "shard_count");
    String shardPattern = requiredText(node, "shard_pattern");
    return new CsvNameIndexShardConfig(directory, shardCount, shardPattern);
  }

  private String requiredText(JsonNode node, String fieldName) throws IOException {
    JsonNode value = node.path(fieldName);
    if (value.isMissingNode() || value.asText().isBlank()) {
      throw new IOException("Missing manifest field: " + fieldName);
    }
    return value.asText();
  }

  private int requiredPositiveInt(JsonNode node, String fieldName) throws IOException {
    JsonNode value = node.path(fieldName);
    if (!value.canConvertToInt() || value.asInt() <= 0) {
      throw new IOException("Missing or invalid positive integer manifest field: " + fieldName);
    }
    return value.asInt();
  }
}

record CsvNameIndexManifest(
    String schemaVersion,
    CsvNameIndexShardConfig tokenPostings,
    CsvNameIndexShardConfig productMetadata
) {}

record CsvNameIndexShardConfig(
    String directory,
    int shardCount,
    String shardPattern
) {}
