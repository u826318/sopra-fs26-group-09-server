package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductSearchResponseDTO;
import ch.uzh.ifi.hase.soprafs26.service.OpenFoodFactsService;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetLookupService;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetNameSearchService;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetProductMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ProductController {

    private final OpenFoodFactsService openFoodFactsService;
    private final LocalDatasetLookupService localDatasetLookupService;
    private final LocalDatasetProductMapper localDatasetProductMapper;
    private final LocalDatasetNameSearchService localDatasetNameSearchService;

    public ProductController(
            OpenFoodFactsService openFoodFactsService,
            LocalDatasetLookupService localDatasetLookupService,
            LocalDatasetProductMapper localDatasetProductMapper,
            LocalDatasetNameSearchService localDatasetNameSearchService
    ) {
        this.openFoodFactsService = openFoodFactsService;
        this.localDatasetLookupService = localDatasetLookupService;
        this.localDatasetProductMapper = localDatasetProductMapper;
        this.localDatasetNameSearchService = localDatasetNameSearchService;
    }

    @GetMapping("/products/lookup")
    public Object lookupByBarcode(@RequestParam("barcode") String barcode) {
        return lookupProductByBarcode(barcode);
    }

    @GetMapping("/products/lookup/{barcode}")
    public Object lookupByBarcodePath(@PathVariable("barcode") String barcode) {
        return lookupProductByBarcode(barcode);
    }

    @GetMapping("/products/index/{productIndex}")
    public LocalDatasetProductDTO lookupByProductIndex(@PathVariable("productIndex") Long productIndex) {
        return localDatasetLookupService.findRawRowByProductIndex(productIndex)
                .map(localDatasetProductMapper::toDto)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found for productIndex: " + productIndex));
    }

    @GetMapping("/products/search")
    public LocalDatasetProductSearchResponseDTO searchByName(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        return localDatasetNameSearchService.search(query, limit);
    }

    private Object lookupProductByBarcode(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Barcode must not be empty.");
        }
        String normalizedBarcode = barcode.trim();

        try {
            return openFoodFactsService.lookupByBarcode(normalizedBarcode);
        } catch (ResponseStatusException offException) {
            return localDatasetLookupService.findRawRowByBarcode(normalizedBarcode)
                    .map(localDatasetProductMapper::toDto)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Product not found for barcode: " + normalizedBarcode));
        }
    }
}
