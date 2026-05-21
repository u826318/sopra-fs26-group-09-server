package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

@Component
public class CsvShardResolver {

  public String tokenPostingsShardResource(CsvNameIndexManifest manifest, String token) {
    CsvNameIndexShardConfig config = manifest.tokenPostings();
    int shard = tokenShard(token, config.shardCount());
    return shardResource(config, shard);
  }

  public String productMetadataShardResource(CsvNameIndexManifest manifest, Long productIndex) {
    CsvNameIndexShardConfig config = manifest.productMetadata();
    int shard = productMetadataShard(productIndex, config.shardCount());
    return shardResource(config, shard);
  }

  public int productMetadataShard(Long productIndex, int shardCount) {
    if (productIndex == null) {
      throw new IllegalArgumentException("productIndex must not be null");
    }
    return Math.floorMod(productIndex, shardCount);
  }

  int tokenShard(String token, int shardCount) {
    CRC32 crc32 = new CRC32();
    byte[] bytes = (token == null ? "" : token).getBytes(StandardCharsets.UTF_8);
    crc32.update(bytes, 0, bytes.length);
    return (int) (crc32.getValue() % shardCount);
  }

  private String shardResource(CsvNameIndexShardConfig config, int shard) {
    return CsvNameIndexManifestLoader.NAME_INDEX_ROOT
        + "/"
        + config.directory()
        + "/"
        + String.format(config.shardPattern(), shard);
  }
}
