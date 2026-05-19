package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.rest.dto.BarcodeExtractionResponseDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ProductDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductDTO;
import ch.uzh.ifi.hase.soprafs26.service.BarcodeExtractionService;
import ch.uzh.ifi.hase.soprafs26.service.OpenFoodFactsService;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetLookupService;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetProductMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class ProductController {

  private final OpenFoodFactsService openFoodFactsService;
  private final BarcodeExtractionService barcodeExtractionService;
  private final LocalDatasetLookupService localDatasetLookupService;
  private final LocalDatasetProductMapper localDatasetProductMapper;

  public ProductController(
      OpenFoodFactsService openFoodFactsService,
      BarcodeExtractionService barcodeExtractionService,
      LocalDatasetLookupService localDatasetLookupService,
      LocalDatasetProductMapper localDatasetProductMapper
  ) {
    this.openFoodFactsService = openFoodFactsService;
    this.barcodeExtractionService = barcodeExtractionService;
    this.localDatasetLookupService = localDatasetLookupService;
    this.localDatasetProductMapper = localDatasetProductMapper;
  }

  @GetMapping("/products/lookup")
  @ResponseStatus(HttpStatus.OK)
  public LocalDatasetProductDTO lookupByBarcode(@RequestParam("barcode") String barcode) {
    return lookupLocalDatasetProductByBarcode(barcode);
  }

  @GetMapping("/products/barcode/{barcode}")
  @ResponseStatus(HttpStatus.OK)
  public LocalDatasetProductDTO lookupByBarcodePath(@PathVariable("barcode") String barcode) {
    return lookupLocalDatasetProductByBarcode(barcode);
  }

  @GetMapping("/products/search")
  @ResponseStatus(HttpStatus.OK)
  public List<ProductDTO> search(
      @RequestParam("q") String query,
      @RequestParam(value = "limit", defaultValue = "12") int limit
  ) {
    return openFoodFactsService.search(query, limit);
  }

  private LocalDatasetProductDTO lookupLocalDatasetProductByBarcode(String barcode) {
    return localDatasetLookupService.findRawRowByBarcode(barcode)
        .map(localDatasetProductMapper::toDto)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "No local dataset product found for barcode " + barcode
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
