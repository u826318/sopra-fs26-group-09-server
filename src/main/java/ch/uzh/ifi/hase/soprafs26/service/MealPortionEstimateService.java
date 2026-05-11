package ch.uzh.ifi.hase.soprafs26.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ch.uzh.ifi.hase.soprafs26.entity.PantryItem;
import ch.uzh.ifi.hase.soprafs26.rest.dto.PortionEstimateResponseDTO;

@Service
public class MealPortionEstimateService {

    public PortionEstimateResponseDTO estimatePortion(PantryItem pantryItem, MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("Meal photo must not be empty.");
        }

        // Issue #135 — fallback until external CV service is wired.
        PortionEstimateResponseDTO response = new PortionEstimateResponseDTO();
        response.setSuggestedAmount(null);
        response.setEstimatedRange(null);
        response.setManualFallback(true);
        response.setMessage(
                "Automatic portion estimation is currently unavailable. Please enter the consumed amount manually."
        );

        return response;
    }
}