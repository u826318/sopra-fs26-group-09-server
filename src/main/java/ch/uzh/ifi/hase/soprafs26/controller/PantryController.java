package ch.uzh.ifi.hase.soprafs26.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ch.uzh.ifi.hase.soprafs26.rest.dto.ConsumePantryItemPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ConsumePantryItemResponseDTO;
import ch.uzh.ifi.hase.soprafs26.service.PantryService;

@RestController
public class PantryController {

    private final PantryService pantryService;

    public PantryController(PantryService pantryService) {
        this.pantryService = pantryService;
    }

    @PostMapping("/households/{householdId}/pantry/{itemId}/consume")
    @ResponseStatus(HttpStatus.OK)
    public ConsumePantryItemResponseDTO consumePantryItem(
            @RequestAttribute("authenticatedUserId") Long authenticatedUserId,
            @PathVariable Long householdId,
            @PathVariable Long itemId,
            @RequestBody ConsumePantryItemPostDTO consumePostDTO) {

        PantryService.ConsumeResult result = pantryService.consumeItem(
                householdId,
                itemId,
                consumePostDTO.getQuantity(),
                authenticatedUserId
        );

        ConsumePantryItemResponseDTO responseDTO = new ConsumePantryItemResponseDTO();
        responseDTO.setItemId(result.getItemId());
        responseDTO.setRemainingCount(result.getRemainingCount());
        responseDTO.setConsumedCalories(result.getConsumedCalories());
        responseDTO.setRemoved(result.isRemoved());

        return responseDTO;
    }
}