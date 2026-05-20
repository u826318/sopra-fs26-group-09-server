package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class NameSearchIndexRepository {

  private static final String INDEX_RESOURCE = "local-dataset/name-index.sqlite";

  private volatile Path extractedIndexPath;

  public Connection openConnection() throws SQLException, IOException {
    Path indexPath = getIndexPath();
    return DriverManager.getConnection("jdbc:sqlite:" + indexPath.toAbsolutePath());
  }

  public List<TokenInfo> loadTokenInfo(Connection connection, List<String> tokens) throws SQLException {
    String sql = "SELECT token_id, product_count FROM tokens WHERE token = ?";
    Map<String, TokenInfo> infoByToken = new LinkedHashMap<>();

    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      for (String token : tokens) {
        stmt.setString(1, token);
        try (ResultSet rs = stmt.executeQuery()) {
          if (rs.next()) {
            infoByToken.put(token, new TokenInfo(token, rs.getLong("token_id"), rs.getInt("product_count")));
          }
        }
      }
    }

    return infoByToken.values().stream()
        .sorted(Comparator.comparingInt(TokenInfo::productCount).reversed())
        .toList();
  }

  public CandidateSelection selectAnchorsAndCandidates(
      Connection connection,
      List<TokenInfo> tokenInfos,
      int targetCandidateCount,
      int queryCandidateLimit
  ) throws SQLException {
    List<TokenInfo> anchors = new ArrayList<>();
    Set<Long> currentCandidates = new LinkedHashSet<>();
    boolean tooBroad = true;

    for (TokenInfo tokenInfo : tokenInfos) {
      anchors.add(tokenInfo);
      currentCandidates = loadIntersectionCandidates(connection, anchors, queryCandidateLimit);

      if (currentCandidates.isEmpty()) {
        return new CandidateSelection(currentCandidates, anchorTokenSet(anchors), false);
      }

      if (currentCandidates.size() <= targetCandidateCount) {
        tooBroad = false;
        break;
      }
    }

    return new CandidateSelection(
        currentCandidates,
        anchorTokenSet(anchors),
        tooBroad && currentCandidates.size() > targetCandidateCount
    );
  }

  private Set<Long> loadIntersectionCandidates(Connection connection, List<TokenInfo> anchors, int limit) throws SQLException {
    if (anchors.isEmpty()) {
      return Collections.emptySet();
    }

    String placeholders = anchors.stream().map(ignored -> "?").collect(Collectors.joining(", "));
    String sql = "SELECT product_index "
        + "FROM postings "
        + "WHERE token_id IN (" + placeholders + ") "
        + "GROUP BY product_index "
        + "HAVING COUNT(DISTINCT token_id) = ? "
        + "LIMIT ?";

    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      int parameterIndex = 1;
      for (TokenInfo anchor : anchors) {
        stmt.setLong(parameterIndex, anchor.tokenId());
        parameterIndex += 1;
      }
      stmt.setInt(parameterIndex, anchors.size());
      stmt.setInt(parameterIndex + 1, limit);

      try (ResultSet rs = stmt.executeQuery()) {
        Set<Long> candidates = new LinkedHashSet<>();
        while (rs.next()) {
          candidates.add(rs.getLong("product_index"));
        }
        return candidates;
      }
    }
  }

  private Set<String> anchorTokenSet(List<TokenInfo> anchors) {
    return anchors.stream()
        .map(TokenInfo::token)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private Path getIndexPath() throws IOException {
    Path current = extractedIndexPath;
    if (current != null && Files.exists(current)) {
      return current;
    }

    synchronized (this) {
      if (extractedIndexPath != null && Files.exists(extractedIndexPath)) {
        return extractedIndexPath;
      }

      ClassPathResource resource = new ClassPathResource(INDEX_RESOURCE);
      if (!resource.exists()) {
        throw new IOException("Missing classpath resource: " + INDEX_RESOURCE);
      }

      if (resource.isFile()) {
        extractedIndexPath = resource.getFile().toPath();
        return extractedIndexPath;
      }

      Path tempFile = Files.createTempFile("local-product-name-index-", ".sqlite");
      tempFile.toFile().deleteOnExit();
      try (InputStream inputStream = resource.getInputStream()) {
        Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
      extractedIndexPath = tempFile;
      return extractedIndexPath;
    }
  }
}
