package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

@Component
public class CsvProductMetadataRepository {

  private static final Logger log = LoggerFactory.getLogger(CsvProductMetadataRepository.class);

  private final CsvNameIndexManifestLoader manifestLoader;
  private final CsvShardResolver shardResolver;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public CsvProductMetadataRepository(
      CsvNameIndexManifestLoader manifestLoader,
      CsvShardResolver shardResolver
  ) {
    this.manifestLoader = manifestLoader;
    this.shardResolver = shardResolver;
  }

  public List<ProductRow> resolveProductRows(Set<Long> productIndices) throws IOException {
    if (productIndices == null || productIndices.isEmpty()) {
      return List.of();
    }

    CsvNameIndexManifest manifest = manifestLoader.loadManifest();
    Map<Integer, Set<Long>> indicesByShard = groupByMetadataShard(manifest, productIndices);
    Map<Long, ProductRow> rowsByIndex = new LinkedHashMap<>();

    for (Map.Entry<Integer, Set<Long>> entry : indicesByShard.entrySet()) {
      resolveShard(manifest, entry.getKey(), entry.getValue(), rowsByIndex);
    }

    List<ProductRow> rows = new ArrayList<>();
    for (Long productIndex : productIndices) {
      ProductRow row = rowsByIndex.get(productIndex);
      if (row != null) {
        rows.add(row);
      }
    }
    return rows;
  }

  private Map<Integer, Set<Long>> groupByMetadataShard(
      CsvNameIndexManifest manifest,
      Set<Long> productIndices
  ) {
    Map<Integer, Set<Long>> grouped = new LinkedHashMap<>();
    int shardCount = manifest.productMetadata().shardCount();
    for (Long productIndex : productIndices) {
      int shard = shardResolver.productMetadataShard(productIndex, shardCount);
      grouped.computeIfAbsent(shard, ignored -> new LinkedHashSet<>()).add(productIndex);
    }
    return grouped;
  }

  private void resolveShard(
      CsvNameIndexManifest manifest,
      int shard,
      Set<Long> requestedIndices,
      Map<Long, ProductRow> rowsByIndex
  ) throws IOException {
    if (requestedIndices.isEmpty()) {
      return;
    }

    String resourcePath = CsvNameIndexManifestLoader.NAME_INDEX_ROOT
        + "/"
        + manifest.productMetadata().directory()
        + "/"
        + String.format(manifest.productMetadata().shardPattern(), shard);
    ClassPathResource resource = new ClassPathResource(resourcePath);
    if (!resource.exists()) {
      throw new IOException("Missing product-metadata shard: " + resourcePath);
    }

    int resolvedInShard = 0;
    try (
        Reader reader = new InputStreamReader(new GZIPInputStream(resource.getInputStream()), StandardCharsets.UTF_8);
        CSVParser parser = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .build()
            .parse(reader)
    ) {
      for (CSVRecord record : parser) {
        Long productIndex = parseProductIndex(record.get("product_index"), resourcePath);
        if (!requestedIndices.contains(productIndex)) {
          continue;
        }

        String brands = clean(record.get("brands"));
        String rawNameCandidates = clean(record.get("name_candidates"));
        List<String> nameCandidates = parseNameCandidates(rawNameCandidates);
        String displayName = firstNonBlank(nameCandidates);

        rowsByIndex.put(productIndex, new ProductRow(
            productIndex,
            null,
            displayName,
            brands,
            null,
            buildSearchText(brands, nameCandidates)
        ));
        resolvedInShard += 1;

        if (resolvedInShard >= requestedIndices.size()) {
          break;
        }
      }
    }

    log.debug(
        "[NAME_INDEX_CSV] metadataShard={} requested={} resolved={}",
        shard,
        requestedIndices.size(),
        resolvedInShard
    );
  }

  private Long parseProductIndex(String rawProductIndex, String resourcePath) throws IOException {
    try {
      return Long.parseLong(rawProductIndex);
    }
    catch (NumberFormatException ex) {
      throw new IOException("Invalid product_index in product-metadata shard " + resourcePath, ex);
    }
  }

  private List<String> parseNameCandidates(String rawNameCandidates) {
    if (rawNameCandidates == null || rawNameCandidates.isBlank()) {
      return List.of();
    }

    try {
      List<String> values = objectMapper.readValue(rawNameCandidates, new TypeReference<List<String>>() {});
      return values.stream()
          .map(this::clean)
          .filter(value -> value != null && !value.isBlank())
          .distinct()
          .toList();
    }
    catch (Exception ignored) {
      String fallback = clean(rawNameCandidates);
      return fallback == null || fallback.isBlank() ? List.of() : List.of(fallback);
    }
  }

  private String firstNonBlank(List<String> values) {
    return values.stream()
        .filter(value -> value != null && !value.isBlank())
        .findFirst()
        .orElse(null);
  }

  private String buildSearchText(String brands, List<String> nameCandidates) {
    return java.util.stream.Stream.concat(
            java.util.stream.Stream.of(brands),
            nameCandidates.stream()
        )
        .filter(value -> value != null && !value.isBlank())
        .collect(Collectors.joining(" "));
  }

  private String clean(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
