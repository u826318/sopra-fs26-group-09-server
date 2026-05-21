package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Component
public class LocalDatasetProductIndexResolver {

  private final CsvProductMetadataRepository productMetadataRepository;

  public LocalDatasetProductIndexResolver(CsvProductMetadataRepository productMetadataRepository) {
    this.productMetadataRepository = productMetadataRepository;
  }

  public List<ProductRow> resolveProductRows(Set<Long> productIndices) throws IOException {
    return productMetadataRepository.resolveProductRows(productIndices);
  }
}
