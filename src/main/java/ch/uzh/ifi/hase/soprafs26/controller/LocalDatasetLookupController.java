package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductDTO;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetLookupService;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetProductMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/local-dataset/products")
public class LocalDatasetLookupController {

  private final LocalDatasetLookupService localDatasetLookupService;
  private final LocalDatasetProductMapper localDatasetProductMapper;

  public LocalDatasetLookupController(
      LocalDatasetLookupService localDatasetLookupService,
      LocalDatasetProductMapper localDatasetProductMapper
  ) {
    this.localDatasetLookupService = localDatasetLookupService;
    this.localDatasetProductMapper = localDatasetProductMapper;
  }

  @GetMapping("/lookup")
  @ResponseStatus(HttpStatus.OK)
  public LocalDatasetProductDTO lookupByBarcode(@RequestParam("barcode") String barcode) {
    return localDatasetLookupService.findRawRowByBarcode(barcode)
        .map(localDatasetProductMapper::toDto)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "No local dataset product found for barcode " + barcode
        ));
  }
}
