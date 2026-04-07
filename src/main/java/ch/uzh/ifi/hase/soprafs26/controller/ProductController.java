package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.rest.dto.ProductDTO;
import ch.uzh.ifi.hase.soprafs26.service.OpenFoodFactsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ProductController {

  private final OpenFoodFactsService openFoodFactsService;

  public ProductController(OpenFoodFactsService openFoodFactsService) {
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

  @GetMapping("/products/search")
  @ResponseStatus(HttpStatus.OK)
  public List<ProductDTO> search(
      @RequestParam("q") String query,
      @RequestParam(value = "limit", defaultValue = "12") int limit
  ) {
    return openFoodFactsService.search(query, limit);
  }

}
