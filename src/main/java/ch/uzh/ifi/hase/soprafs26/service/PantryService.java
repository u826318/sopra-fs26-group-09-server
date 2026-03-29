package ch.uzh.ifi.hase.soprafs26.service;

import java.util.List;

import org.springframework.stereotype.Service;

import ch.uzh.ifi.hase.soprafs26.entity.PantryItem;
import ch.uzh.ifi.hase.soprafs26.repository.PantryItemRepository;

@Service
public class PantryService {

    private final PantryItemRepository pantryItemRepository;

    public PantryService(PantryItemRepository pantryItemRepository) {
        this.pantryItemRepository = pantryItemRepository;
    }

    /**
     * Calculates the total calories currently stored in the pantry.
     * Formula: sum(kcalPerPackage * count)
     */
    public double calculateTotalCalories() {
        List<PantryItem> pantryItems = pantryItemRepository.findAll();

        double totalCalories = 0.0;

        for (PantryItem item : pantryItems) {
            if (item.getKcalPerPackage() != null && item.getCount() != null) {
                totalCalories += item.getKcalPerPackage() * item.getCount();
            }
        }

        return totalCalories;
    }
}