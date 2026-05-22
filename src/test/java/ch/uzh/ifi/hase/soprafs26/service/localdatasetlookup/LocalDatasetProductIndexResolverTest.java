package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalDatasetProductIndexResolverTest {

    private CsvProductMetadataRepository mockRepository;
    private LocalDatasetProductIndexResolver resolver;

    @BeforeEach
    void setUp() {
        mockRepository = mock(CsvProductMetadataRepository.class);
        resolver = new LocalDatasetProductIndexResolver(mockRepository);
    }

    @Test
    void resolveProductRows_delegatesToRepository() throws IOException {
        Set<Long> indices = Set.of(1L, 2L);
        List<ProductRow> expected = List.of(
                new ProductRow(1L, "111", "ProductA", "BrandA", null, "BrandA ProductA"),
                new ProductRow(2L, "222", "ProductB", "BrandB", null, "BrandB ProductB")
        );
        when(mockRepository.resolveProductRows(indices)).thenReturn(expected);

        List<ProductRow> result = resolver.resolveProductRows(indices);

        assertSame(expected, result);
        verify(mockRepository, times(1)).resolveProductRows(indices);
    }
}
