package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

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
import java.util.Optional;
import java.util.zip.GZIPInputStream;

@Component
public class CsvTokenPostingsRepository {

  private static final Logger log = LoggerFactory.getLogger(CsvTokenPostingsRepository.class);

  private final CsvNameIndexManifestLoader manifestLoader;
  private final CsvShardResolver shardResolver;
  private final CsvPostingDecoder postingDecoder;

  public CsvTokenPostingsRepository(
      CsvNameIndexManifestLoader manifestLoader,
      CsvShardResolver shardResolver,
      CsvPostingDecoder postingDecoder
  ) {
    this.manifestLoader = manifestLoader;
    this.shardResolver = shardResolver;
    this.postingDecoder = postingDecoder;
  }

  public Optional<TokenInfo> findToken(String token) throws IOException {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }

    CsvNameIndexManifest manifest = manifestLoader.loadManifest();
    String resourcePath = shardResolver.tokenPostingsShardResource(manifest, token);
    ClassPathResource resource = new ClassPathResource(resourcePath);
    if (!resource.exists()) {
      throw new IOException("Missing token-postings shard: " + resourcePath);
    }

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
        String rowToken = record.get("token");
        if (!token.equals(rowToken)) {
          continue;
        }

        int productCount = parseProductCount(record.get("product_count"), token, resourcePath);
        String postingEncoding = record.get("posting_encoding");
        String encodedProductIndices = record.get("product_indices");
        TokenInfo info = new TokenInfo(
            token,
            productCount,
            postingDecoder.decode(postingEncoding, encodedProductIndices)
        );
        log.debug(
            "[NAME_INDEX_CSV] token='{}' shardResource='{}' found=true productCount={} decodedPostings={}",
            token,
            resourcePath,
            productCount,
            info.productIndices().size()
        );
        return Optional.of(info);
      }
    }

    log.debug("[NAME_INDEX_CSV] token='{}' shardResource='{}' found=false", token, resourcePath);
    return Optional.empty();
  }

  private int parseProductCount(String rawProductCount, String token, String resourcePath) throws IOException {
    try {
      return Integer.parseInt(rawProductCount);
    }
    catch (NumberFormatException ex) {
      throw new IOException(
          "Invalid product_count for token '" + token + "' in token-postings shard " + resourcePath,
          ex
      );
    }
  }
}
