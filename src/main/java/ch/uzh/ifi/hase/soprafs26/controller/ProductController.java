package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.rest.dto.BarcodeExtractionResponseDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ProductDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductSearchResponseDTO;
import ch.uzh.ifi.hase.soprafs26.service.BarcodeExtractionService;
import ch.uzh.ifi.hase.soprafs26.service.OpenFoodFactsService;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetLookupService;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetNameSearchService;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetProductMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ProductController {

  private final BarcodeExtractionService barcodeExtractionService;
  private final LocalDatasetLookupService localDatasetLookupService;
  private final LocalDatasetProductMapper localDatasetProductMapper;
  private final LocalDatasetNameSearchService localDatasetNameSearchService;
  private final OpenFoodFactsService openFoodFactsService;

  public ProductController(
      BarcodeExtractionService barcodeExtractionService,
      LocalDatasetLookupService localDatasetLookupService,
      LocalDatasetProductMapper localDatasetProductMapper,
      LocalDatasetNameSearchService localDatasetNameSearchService,
      OpenFoodFactsService openFoodFactsService
  ) {
    this.barcodeExtractionService = barcodeExtractionService;
    this.localDatasetLookupService = localDatasetLookupService;
    this.localDatasetProductMapper = localDatasetProductMapper;
    this.localDatasetNameSearchService = localDatasetNameSearchService;
    this.openFoodFactsService = openFoodFactsService;
  }

  @GetMapping("/products/lookup")
  @ResponseStatus(HttpStatus.OK)
  public ProductDTO lookupByBarcode(@RequestParam("barcode") String barcode) {
    return openFoodFactsService.lookupByBarcode(barcode);
  }

  @GetMapping("/products/barcode/{barcode}")
  @ResponseStatus(HttpStatus.OK)
  public ProductDTO lookupByBarcodePath(@PathVariable("barcode") String barcode) {
    return openFoodFactsService.lookupByBarcode(barcode);
  }

  @GetMapping("/products/index/{productIndex}")
  @ResponseStatus(HttpStatus.OK)
  public LocalDatasetProductDTO lookupByProductIndexPath(@PathVariable("productIndex") Long productIndex) {
    return lookupLocalDatasetProductByProductIndex(productIndex);
  }

  @GetMapping("/products/lookup-by-index")
  @ResponseStatus(HttpStatus.OK)
  public LocalDatasetProductDTO lookupByProductIndexQuery(@RequestParam("productIndex") Long productIndex) {
    return lookupLocalDatasetProductByProductIndex(productIndex);
  }

  @GetMapping("/products/search")
  @ResponseStatus(HttpStatus.OK)
  public LocalDatasetProductSearchResponseDTO search(
      @RequestParam("q") String query,
      @RequestParam(value = "limit", defaultValue = "10") int limit
  ) {
    return localDatasetNameSearchService.search(query, limit);
  }

  private LocalDatasetProductDTO lookupLocalDatasetProductByBarcode(String barcode) {
    return localDatasetLookupService.findRawRowByBarcode(barcode)
        .map(localDatasetProductMapper::toDto)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "No local dataset product found for barcode " + barcode
        ));
  }

  private LocalDatasetProductDTO lookupLocalDatasetProductByProductIndex(Long productIndex) {
    return localDatasetLookupService.findRawRowByProductIndex(productIndex)
        .map(localDatasetProductMapper::toDto)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "No local dataset product found for product index " + productIndex
        ));
  }

  @PostMapping(value = "/products/barcode/extract", consumes = "multipart/form-data")
  @ResponseStatus(HttpStatus.OK)
  public BarcodeExtractionResponseDTO extractBarcodeFromImage(@RequestPart("image") MultipartFile image) {
    String barcode = barcodeExtractionService.extractBarcode(image);
    BarcodeExtractionResponseDTO response = new BarcodeExtractionResponseDTO();
    response.setBarcode(barcode);
    return response;
  }

}
