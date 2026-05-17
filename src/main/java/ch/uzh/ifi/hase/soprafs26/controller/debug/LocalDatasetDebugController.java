package ch.uzh.ifi.hase.soprafs26.controller.debug;

import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetLookupService;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetProductMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/debug/local-dataset")
public class LocalDatasetDebugController {

  private final LocalDatasetLookupService localDatasetLookupService;
  private final LocalDatasetProductMapper localDatasetProductMapper;

  public LocalDatasetDebugController(
      LocalDatasetLookupService localDatasetLookupService,
      LocalDatasetProductMapper localDatasetProductMapper
  ) {
    this.localDatasetLookupService = localDatasetLookupService;
    this.localDatasetProductMapper = localDatasetProductMapper;
  }

  @GetMapping("/raw-row")
  public ResponseEntity<?> findRawRowByBarcode(@RequestParam("barcode") String barcode) {
    return localDatasetLookupService.findRawRowByBarcode(barcode)
        .<ResponseEntity<?>>map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            Map.of(
                "message", "No local dataset row found for barcode " + barcode
            )
        ));
  }

  @GetMapping("/product")
  public ResponseEntity<?> findProductByBarcode(@RequestParam("barcode") String barcode) {
    return localDatasetLookupService.findRawRowByBarcode(barcode)
        .<ResponseEntity<?>>map(row -> ResponseEntity.ok(localDatasetProductMapper.toDto(row)))
        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            Map.of(
                "message", "No local dataset row found for barcode " + barcode
            )
        ));
  }
}
