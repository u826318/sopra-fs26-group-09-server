package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class LocalDatasetProductIndexResolver {

  private static final int SQLITE_PARAMETER_LIMIT_SAFE_CHUNK = 900;

  private final ObjectMapper objectMapper = new ObjectMapper();

  public List<ProductRow> resolveProductRows(Connection connection, Set<Long> productIndices) throws SQLException {
    if (productIndices == null || productIndices.isEmpty()) {
      return List.of();
    }

    Map<Long, ProductRow> rowsByIndex = new LinkedHashMap<>();
    List<Long> indices = new ArrayList<>(productIndices);

    for (int start = 0; start < indices.size(); start += SQLITE_PARAMETER_LIMIT_SAFE_CHUNK) {
      int end = Math.min(start + SQLITE_PARAMETER_LIMIT_SAFE_CHUNK, indices.size());
      loadMetadataChunk(connection, indices.subList(start, end), rowsByIndex);
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

  private void loadMetadataChunk(
      Connection connection,
      List<Long> productIndices,
      Map<Long, ProductRow> rowsByIndex
  ) throws SQLException {
    if (productIndices.isEmpty()) {
      return;
    }

    String placeholders = productIndices.stream().map(ignored -> "?").collect(Collectors.joining(", "));
    String sql = "SELECT product_index, brands, name_candidates "
        + "FROM product_metadata "
        + "WHERE product_index IN (" + placeholders + ")";

    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      for (int i = 0; i < productIndices.size(); i += 1) {
        stmt.setLong(i + 1, productIndices.get(i));
      }

      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          Long productIndex = rs.getLong("product_index");
          String brands = clean(rs.getString("brands"));
          String rawNameCandidates = clean(rs.getString("name_candidates"));
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
        }
      }
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
