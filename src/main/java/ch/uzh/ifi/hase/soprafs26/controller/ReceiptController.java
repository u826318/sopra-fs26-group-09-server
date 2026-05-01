package ch.uzh.ifi.hase.soprafs26.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ch.uzh.ifi.hase.soprafs26.rest.dto.ReceiptUploadResponseDTO;
import ch.uzh.ifi.hase.soprafs26.service.ReceiptUploadService;

@RestController
public class ReceiptController {

    private final ReceiptUploadService receiptUploadService;

    public ReceiptController(ReceiptUploadService receiptUploadService) {
        this.receiptUploadService = receiptUploadService;
    }

    @PostMapping(value = "/households/{householdId}/receipt/upload", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.OK)
    public ReceiptUploadResponseDTO uploadReceipt(
            @RequestAttribute("authenticatedUserId") Long authenticatedUserId,
            @PathVariable Long householdId,
            @RequestPart("image") MultipartFile image) {

        return receiptUploadService.uploadReceipt(householdId, authenticatedUserId, image);
    }
}
