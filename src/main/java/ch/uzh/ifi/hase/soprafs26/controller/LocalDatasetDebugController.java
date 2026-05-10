package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetLookupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class LocalDatasetDebugController {

  private final LocalDatasetLookupService localDatasetLookupService;

  public LocalDatasetDebugController(LocalDatasetLookupService localDatasetLookupService) {
    this.localDatasetLookupService = localDatasetLookupService;
  }

  @GetMapping("/debug/local-dataset/raw-row")
  public ResponseEntity<?> findRawRowByBarcode(@RequestParam("barcode") String barcode) {
    return localDatasetLookupService.findRawRowByBarcode(barcode)
        .<ResponseEntity<?>>map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            Map.of(
                "message", "No local dataset row found for barcode " + barcode
            )
        ));
  }
}