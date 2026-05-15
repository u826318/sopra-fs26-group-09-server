package ch.uzh.ifi.hase.soprafs26.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.entity.Household;
import ch.uzh.ifi.hase.soprafs26.entity.HouseholdMember;
import ch.uzh.ifi.hase.soprafs26.entity.HouseholdMemberId;
import ch.uzh.ifi.hase.soprafs26.entity.PantryItem;
import ch.uzh.ifi.hase.soprafs26.entity.UserHealthGoal;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdMemberRepository;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdRepository;
import ch.uzh.ifi.hase.soprafs26.repository.PantryItemRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserHealthGoalRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.RecipeCookResponseDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.RecipeRecommendationGetDTO;

class RecipeServiceTest {

    private HouseholdRepository householdRepository;
    private HouseholdMemberRepository householdMemberRepository;
    private PantryItemRepository pantryItemRepository;
    private UserHealthGoalRepository userHealthGoalRepository;
    private PantryService pantryService;
    private RecipeService recipeService;

    @BeforeEach
    void setUp() {
        householdRepository = mock(HouseholdRepository.class);
        householdMemberRepository = mock(HouseholdMemberRepository.class);
        pantryItemRepository = mock(PantryItemRepository.class);
        userHealthGoalRepository = mock(UserHealthGoalRepository.class);
        pantryService = mock(PantryService.class);
        recipeService = new RecipeService(
                householdRepository,
                householdMemberRepository,
                pantryItemRepository,
                userHealthGoalRepository,
                pantryService);

        Household household = new Household();
        household.setId(1L);
        when(householdRepository.findById(1L)).thenReturn(Optional.of(household));
        when(householdMemberRepository.existsById(any(HouseholdMemberId.class))).thenReturn(true);
        when(householdMemberRepository.findByIdHouseholdId(1L)).thenReturn(List.of(member(99L)));
        when(userHealthGoalRepository.findByUserIdIn(List.of(99L))).thenReturn(List.of(goal("GAIN_MUSCLE")));
    }

    @Test
    void getRecommendations_prioritizesReadyHighProteinPantryMatch() {
        when(pantryItemRepository.findByHouseholdId(1L)).thenReturn(List.of(
                item(10L, "Chicken breast", 500.0, "g"),
                item(11L, "Rice", 500.0, "g"),
                item(12L, "Broccoli", 400.0, "g"),
                item(13L, "Olive oil", 100.0, "ml")));

        List<RecipeRecommendationGetDTO> result = recipeService.getRecommendations(1L, 99L);

        assertFalse(result.isEmpty());
        RecipeRecommendationGetDTO dynamicHighProteinRecipe = result.stream()
                .filter(recipe -> recipe.getId().startsWith("dynamic-"))
                .filter(RecipeRecommendationGetDTO::isReadyToCook)
                .filter(recipe -> recipe.getHealthGoalFit().contains("high-protein"))
                .findFirst()
                .orElseThrow();

        assertTrue(dynamicHighProteinRecipe.getRecommendationReason().contains("Chicken breast"));
        assertTrue(dynamicHighProteinRecipe.getMatchedIngredientCount() >= 2);
        assertEquals(0, dynamicHighProteinRecipe.getMissingIngredientCount());
    }

    @Test
    void getRecommendations_returnsMissingIngredientsWhenPantryIsIncomplete() {
        when(pantryItemRepository.findByHouseholdId(1L)).thenReturn(List.of(
                item(10L, "Pasta", 300.0, "g")));
        when(userHealthGoalRepository.findByUserIdIn(List.of(99L))).thenReturn(List.of(goal("LOSE_WEIGHT")));

        List<RecipeRecommendationGetDTO> result = recipeService.getRecommendations(1L, 99L);

        RecipeRecommendationGetDTO pasta = result.stream()
                .filter(recipe -> "tomato-pasta-light".equals(recipe.getId()))
                .findFirst()
                .orElseThrow();
        assertFalse(pasta.isReadyToCook());
        assertEquals(1, pasta.getMatchedIngredientCount());
        assertTrue(pasta.getMissingIngredients().contains("Tomato sauce"));
    }

    @Test
    void getRecommendations_generatesToastIdeaFromRealPantryItems() {
        when(pantryItemRepository.findByHouseholdId(1L)).thenReturn(List.of(
                item(10L, "Whole Bread", 1.0, "package"),
                item(11L, "Free Range Eggs", 1.0, "package"),
                item(12L, "Cherry Tomatoes", 250.0, "g")));
        when(userHealthGoalRepository.findByUserIdIn(List.of(99L))).thenReturn(List.of(goal("LOSE_WEIGHT")));

        List<RecipeRecommendationGetDTO> result = recipeService.getRecommendations(1L, 99L);

        RecipeRecommendationGetDTO toast = result.stream()
                .filter(recipe -> recipe.getId().startsWith("dynamic-smart-toast-"))
                .findFirst()
                .orElseThrow();
        assertTrue(toast.isReadyToCook());
        assertTrue(toast.getSummary().contains("Whole Bread"));
        assertTrue(toast.getRecommendationReason().contains("Whole Bread"));
        assertTrue(toast.getHealthGoalFit().contains("lighter calorie"));
    }

    @Test
    void getRecommendations_throwsForbiddenWhenUserIsNotMember() {
        when(householdMemberRepository.existsById(any(HouseholdMemberId.class))).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> recipeService.getRecommendations(1L, 99L));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void cookRecipe_consumesMatchedPantryItems() {
        when(pantryItemRepository.findByHouseholdId(1L)).thenReturn(List.of(
                item(10L, "Pasta", 300.0, "g"),
                item(11L, "Tomato sauce", 300.0, "g"),
                item(12L, "Olive oil", 50.0, "ml")));
        PantryService.ConsumeResult consumeResult = new PantryService.ConsumeResult();
        consumeResult.setConsumedCalories(100.0);
        when(pantryService.consumeItem(eq(1L), any(Long.class), any(Double.class), eq(99L)))
                .thenReturn(consumeResult);

        RecipeCookResponseDTO result = recipeService.cookRecipe(1L, "tomato-pasta-light", 2, 99L);

        assertEquals("tomato-pasta-light", result.getRecipeId());
        assertEquals(2, result.getServingsCooked());
        assertEquals(300.0, result.getConsumedCalories());
        assertEquals(3, result.getConsumedIngredients().size());
        verify(pantryService).consumeItem(1L, 10L, 180.0, 99L);
        verify(pantryService).consumeItem(1L, 11L, 250.0, 99L);
        verify(pantryService).consumeItem(1L, 12L, 10.0, 99L);
    }

    @Test
    void cookRecipe_rejectsRecipeWithMissingIngredients() {
        when(pantryItemRepository.findByHouseholdId(1L)).thenReturn(List.of(item(10L, "Pasta", 300.0, "g")));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> recipeService.cookRecipe(1L, "tomato-pasta-light", 2, 99L));

        assertEquals("Recipe cannot be cooked because required pantry ingredients are missing.", exception.getMessage());
    }

    private HouseholdMember member(Long userId) {
        HouseholdMember member = new HouseholdMember();
        member.setId(new HouseholdMemberId(userId, 1L));
        return member;
    }

    private UserHealthGoal goal(String goalType) {
        UserHealthGoal goal = new UserHealthGoal();
        goal.setUserId(99L);
        goal.setGoalType(goalType);
        goal.setRecommendedDailyCalories(2200.0);
        return goal;
    }

    private PantryItem item(Long id, String name, Double amount, String unit) {
        PantryItem item = new PantryItem();
        item.setId(id);
        item.setHouseholdId(1L);
        item.setName(name);
        item.setAmount(amount);
        item.setAmountUnit(unit);
        item.setKcalPer100g(100.0);
        item.setKcalPer100ml(100.0);
        item.setKcalPerPackage(100.0);
        return item;
    }
}
