package ch.uzh.ifi.hase.soprafs26.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import ch.uzh.ifi.hase.soprafs26.rest.dto.RecipeIngredientGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.RecipeRecommendationGetDTO;

@Service
@Transactional
public class RecipeService {

    private static final int DEFAULT_RECOMMENDATION_LIMIT = 8;
    private static final int MAX_SERVINGS = 12;

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final PantryItemRepository pantryItemRepository;
    private final UserHealthGoalRepository userHealthGoalRepository;
    private final PantryService pantryService;

    public RecipeService(
            HouseholdRepository householdRepository,
            HouseholdMemberRepository householdMemberRepository,
            PantryItemRepository pantryItemRepository,
            UserHealthGoalRepository userHealthGoalRepository,
            PantryService pantryService) {
        this.householdRepository = householdRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.pantryItemRepository = pantryItemRepository;
        this.userHealthGoalRepository = userHealthGoalRepository;
        this.pantryService = pantryService;
    }

    public List<RecipeRecommendationGetDTO> getRecommendations(Long householdId, Long authenticatedUserId) {
        validateMembership(householdId, authenticatedUserId);

        List<PantryItem> pantryItems = pantryItemRepository.findByHouseholdId(householdId);
        List<UserHealthGoal> healthGoals = getHouseholdHealthGoals(householdId);
        HouseholdGoalProfile goalProfile = HouseholdGoalProfile.from(healthGoals);

        return recipesForPantry(pantryItems).stream()
                .map(recipe -> toRecommendation(recipe, pantryItems, goalProfile, recipe.servings()))
                .sorted(Comparator
                        .comparing(RecipeRecommendationGetDTO::isReadyToCook).reversed()
                        .thenComparing(RecipeRecommendationGetDTO::getMatchScore, Comparator.reverseOrder())
                        .thenComparing(RecipeRecommendationGetDTO::getMissingIngredientCount)
                        .thenComparing(RecipeRecommendationGetDTO::getTitle))
                .limit(DEFAULT_RECOMMENDATION_LIMIT)
                .toList();
    }

    public RecipeCookResponseDTO cookRecipe(
            Long householdId,
            String recipeId,
            Integer requestedServings,
            Long authenticatedUserId) {
        validateMembership(householdId, authenticatedUserId);

        List<PantryItem> pantryItems = pantryItemRepository.findByHouseholdId(householdId);
        Recipe recipe = findRecipe(recipeId, pantryItems)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found."));
        int servings = requestedServings == null ? recipe.servings() : requestedServings;
        if (servings <= 0 || servings > MAX_SERVINGS) {
            throw new IllegalArgumentException("Servings must be between 1 and " + MAX_SERVINGS + ".");
        }

        RecipeRecommendationGetDTO recommendation = toRecommendation(
                recipe,
                pantryItems,
                HouseholdGoalProfile.empty(),
                servings);
        if (!recommendation.isReadyToCook()) {
            throw new IllegalArgumentException("Recipe cannot be cooked because required pantry ingredients are missing.");
        }

        double consumedCalories = 0.0;
        List<RecipeIngredientGetDTO> consumedIngredients = new ArrayList<>();
        for (RecipeIngredientGetDTO ingredient : recommendation.getIngredients()) {
            PantryService.ConsumeResult result = pantryService.consumeItem(
                    householdId,
                    ingredient.getPantryItemId(),
                    ingredient.getAmount(),
                    authenticatedUserId);
            if (result.getConsumedCalories() != null) {
                consumedCalories += result.getConsumedCalories();
            }
            consumedIngredients.add(ingredient);
        }

        RecipeCookResponseDTO response = new RecipeCookResponseDTO();
        response.setRecipeId(recipe.id());
        response.setTitle(recipe.title());
        response.setServingsCooked(servings);
        response.setConsumedCalories(round(consumedCalories));
        response.setConsumedIngredients(consumedIngredients);
        return response;
    }

    private Household validateMembership(Long householdId, Long authenticatedUserId) {
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found."));
        HouseholdMemberId membershipId = new HouseholdMemberId(authenticatedUserId, householdId);
        if (!householdMemberRepository.existsById(membershipId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this household.");
        }
        return household;
    }

    private List<UserHealthGoal> getHouseholdHealthGoals(Long householdId) {
        List<Long> memberIds = householdMemberRepository.findByIdHouseholdId(householdId).stream()
                .map(HouseholdMember::getId)
                .map(HouseholdMemberId::getUserId)
                .toList();
        if (memberIds.isEmpty()) {
            return List.of();
        }
        return userHealthGoalRepository.findByUserIdIn(memberIds);
    }

    private Optional<Recipe> findRecipe(String recipeId, List<PantryItem> pantryItems) {
        if (recipeId == null || recipeId.trim().isEmpty()) {
            return Optional.empty();
        }
        return recipesForPantry(pantryItems).stream()
                .filter(recipe -> recipe.id().equals(recipeId))
                .findFirst();
    }

    private RecipeRecommendationGetDTO toRecommendation(
            Recipe recipe,
            List<PantryItem> pantryItems,
            HouseholdGoalProfile goalProfile,
            int servings) {
        double scale = servings / (double) recipe.servings();
        List<RecipeIngredientGetDTO> ingredientDTOs = recipe.ingredients().stream()
                .map(ingredient -> matchIngredient(ingredient, pantryItems, scale))
                .toList();

        int matched = (int) ingredientDTOs.stream()
                .filter(dto -> dto.isMatched() && dto.isEnoughAvailable())
                .count();
        int missing = ingredientDTOs.size() - matched;
        boolean readyToCook = missing == 0;
        int pantryScore = ingredientDTOs.isEmpty() ? 0 : (int) Math.round((matched * 62.0) / ingredientDTOs.size());
        int dynamicPantryBonus = recipe.id().startsWith("dynamic-") ? 12 : 0;
        int readyBonus = readyToCook ? 8 : 0;
        int score = Math.max(0, Math.min(100,
                pantryScore + goalProfile.score(recipe) + dynamicPantryBonus + readyBonus - (missing * 10)));

        RecipeRecommendationGetDTO dto = new RecipeRecommendationGetDTO();
        dto.setId(recipe.id());
        dto.setTitle(recipe.title());
        dto.setSummary(recipe.summary());
        dto.setImageEmoji(recipe.imageToken());
        dto.setServings(servings);
        dto.setReadyToCook(readyToCook);
        dto.setMatchScore(score);
        dto.setMatchedIngredientCount(matched);
        dto.setMissingIngredientCount(missing);
        dto.setHealthGoalFit(goalProfile.fitLabel(recipe));
        dto.setRecommendationReason(buildRecommendationReason(recipe, ingredientDTOs, matched, missing, goalProfile));
        dto.setCaloriesPerServing(recipe.caloriesPerServing());
        dto.setProteinGrams(recipe.proteinGrams());
        dto.setCarbsGrams(recipe.carbsGrams());
        dto.setFatGrams(recipe.fatGrams());
        dto.setTags(recipe.tags());
        dto.setInstructions(recipe.instructions());
        dto.setIngredients(ingredientDTOs);
        dto.setMissingIngredients(ingredientDTOs.stream()
                .filter(ingredient -> !ingredient.isMatched() || !ingredient.isEnoughAvailable())
                .map(RecipeIngredientGetDTO::getName)
                .toList());
        return dto;
    }

    private RecipeIngredientGetDTO matchIngredient(
            RecipeIngredient ingredient,
            List<PantryItem> pantryItems,
            double scale) {
        double requiredAmount = round(ingredient.amount() * scale);
        Optional<PantryItem> bestMatch = pantryItems.stream()
                .filter(item -> ingredient.unit().equals(item.getAmountUnit()))
                .filter(item -> matchesName(item.getName(), ingredient.keywords()))
                .sorted(Comparator.comparing((PantryItem item) -> safeAmount(item.getAmount()) >= requiredAmount).reversed()
                        .thenComparing(PantryItem::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .findFirst();

        RecipeIngredientGetDTO dto = new RecipeIngredientGetDTO();
        dto.setName(ingredient.name());
        dto.setAmount(requiredAmount);
        dto.setUnit(ingredient.unit());
        bestMatch.ifPresent(item -> {
            dto.setMatched(true);
            dto.setPantryItemId(item.getId());
            dto.setPantryItemName(item.getName());
            dto.setAvailableAmount(item.getAmount());
            dto.setPantryUnit(item.getAmountUnit());
            dto.setEnoughAvailable(safeAmount(item.getAmount()) >= requiredAmount);
        });
        if (bestMatch.isEmpty()) {
            dto.setMatched(false);
            dto.setEnoughAvailable(false);
        }
        return dto;
    }

    private boolean matchesName(String pantryName, Collection<String> keywords) {
        String normalizedName = normalize(pantryName);
        if (normalizedName.isEmpty()) {
            return false;
        }
        return keywords.stream()
                .map(this::normalize)
                .anyMatch(keyword -> !keyword.isEmpty()
                        && (normalizedName.contains(keyword) || keyword.contains(normalizedName)));
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private double safeAmount(Double amount) {
        return amount == null ? 0.0 : amount;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String buildRecommendationReason(
            Recipe recipe,
            List<RecipeIngredientGetDTO> ingredients,
            int matched,
            int missing,
            HouseholdGoalProfile goalProfile) {
        String matchedNames = ingredients.stream()
                .filter(ingredient -> ingredient.isMatched() && ingredient.isEnoughAvailable())
                .map(RecipeIngredientGetDTO::getPantryItemName)
                .filter(name -> name != null && !name.isBlank())
                .limit(3)
                .collect(Collectors.joining(", "));
        String missingNames = ingredients.stream()
                .filter(ingredient -> !ingredient.isMatched() || !ingredient.isEnoughAvailable())
                .map(RecipeIngredientGetDTO::getName)
                .limit(3)
                .collect(Collectors.joining(", "));
        String pantryText = missing == 0
                ? "Uses pantry items you already have" + (matchedNames.isBlank() ? "" : ": " + matchedNames)
                : matched + " pantry ingredient(s) matched"
                        + (matchedNames.isBlank() ? "" : " (" + matchedNames + ")")
                        + "; missing or insufficient: " + missingNames;
        String goalText = goalProfile.fitLabel(recipe);
        return pantryText + ". " + goalText + ".";
    }

    private List<Recipe> recipesForPantry(List<PantryItem> pantryItems) {
        List<Recipe> recipes = new ArrayList<>();
        recipes.addAll(dynamicPantryRecipes(pantryItems));
        recipes.addAll(recipeCatalog());
        return recipes;
    }

    private List<Recipe> dynamicPantryRecipes(List<PantryItem> pantryItems) {
        List<PantryItem> usableItems = pantryItems.stream()
                .filter(item -> item.getId() != null)
                .filter(item -> item.getName() != null && !item.getName().trim().isEmpty())
                .filter(item -> safeAmount(item.getAmount()) > 0)
                .filter(item -> Set.of("g", "ml", "package").contains(item.getAmountUnit()))
                .toList();
        if (usableItems.size() < 2) {
            return List.of();
        }

        List<Recipe> recipes = new ArrayList<>();
        List<PantryItem> powerPlateItems = distinctItems(
                findFirstByKeywords(usableItems, "chicken", "tofu", "tuna", "egg", "lentil", "bean", "yogurt").orElse(null),
                findFirstByKeywords(usableItems, "rice", "pasta", "bread", "oat", "potato", "noodle").orElse(null),
                findFirstByKeywords(usableItems, "broccoli", "carrot", "pepper", "tomato", "avocado", "vegetable", "salad").orElse(null),
                findFirstByKeywords(usableItems, "oil", "sauce", "milk").orElse(null));
        if (powerPlateItems.size() >= 2) {
            recipes.add(dynamicRecipe(
                    "dynamic-pantry-power-plate",
                    "Pantry Power Plate",
                    "A made-from-your-pantry plate using " + joinItemNames(powerPlateItems) + ".",
                    "PP",
                    powerPlateItems,
	                    List.of("balanced", "high_protein"),
	                    List.of(
	                            "Measure the pantry items from the impact list, then cut vegetables into bite-size pieces.",
	                            "Cook the starch or base first so it has time to steam and settle.",
	                            "Sear or warm the protein in a hot pan until the edges pick up color.",
	                            "Add the vegetables for the final few minutes so they stay bright and slightly crisp.",
	                            "Spoon the base into a bowl, add protein and vegetables on top, then finish with oil, sauce, salt, or pepper if you have them.")));
        }

        List<PantryItem> lightBowlItems = distinctItems(
                findFirstByKeywords(usableItems, "yogurt", "tofu", "egg", "tuna", "lentil", "bean", "chicken").orElse(null),
                findFirstByKeywords(usableItems, "berry", "fruit", "banana", "apple", "avocado", "tomato", "vegetable").orElse(null),
                findFirstByKeywords(usableItems, "oat", "bread", "rice").orElse(null));
        if (lightBowlItems.size() >= 2) {
            recipes.add(dynamicRecipe(
                    "dynamic-light-pantry-bowl",
                    "Light Pantry Bowl",
                    "A lighter recommendation built from " + joinItemNames(lightBowlItems) + ".",
                    "LB",
                    lightBowlItems,
	                    List.of("low_calorie", "balanced"),
	                    List.of(
	                            "Start with the lightest ingredient as the base, such as yogurt, tofu, vegetables, or fruit.",
	                            "Slice larger pieces thinly so the bowl feels generous without needing huge portions.",
	                            "Warm any cooked ingredient gently, or keep everything cold if this is a fresh bowl.",
	                            "Layer the ingredients instead of mixing them all at once so each bite has contrast.",
	                            "Season lightly and serve right away; add a splash of water, milk, or lemon if it needs loosening.")));
        }

        List<PantryItem> muscleBowlItems = distinctItems(
                findFirstByKeywords(usableItems, "chicken", "tofu", "tuna", "egg", "lentil", "bean", "yogurt").orElse(null),
                findFirstByKeywords(usableItems, "rice", "pasta", "oat", "potato", "bread").orElse(null),
                findFirstByKeywords(usableItems, "milk", "yogurt", "cheese", "egg").orElse(null));
        if (muscleBowlItems.size() >= 2) {
            recipes.add(dynamicRecipe(
                    "dynamic-muscle-builder-bowl",
                    "Muscle Builder Bowl",
                    "A protein-forward recommendation using " + joinItemNames(muscleBowlItems) + ".",
                    "MB",
                    muscleBowlItems,
	                    List.of("high_protein", "balanced"),
	                    List.of(
	                            "Cook the carbohydrate base first, then keep it covered so it stays warm.",
	                            "Prepare the main protein separately and give it enough heat to brown or firm up.",
	                            "Fold in the second protein-rich pantry item near the end so the texture stays creamy or tender.",
	                            "Taste before serving and adjust with salt, pepper, sauce, or a small drizzle of oil.",
	                            "Serve as one post-workout style bowl, or split it into two smaller portions for later.")));
        }

        List<PantryItem> pastaSkilletItems = distinctItems(
                findFirstByKeywords(usableItems, "pasta", "spaghetti", "penne", "noodle").orElse(null),
                findFirstByKeywords(usableItems, "tomato", "sauce", "pesto").orElse(null),
                findFirstByKeywords(usableItems, "chicken", "tuna", "tofu", "cheese", "vegetable", "broccoli", "pepper").orElse(null),
                findFirstByKeywords(usableItems, "oil").orElse(null));
        if (pastaSkilletItems.size() >= 2) {
            recipes.add(dynamicRecipe(
                    "dynamic-pantry-pasta-skillet",
                    "Pantry Pasta Skillet",
                    "A fast skillet-style meal built from " + joinItemNames(pastaSkilletItems) + ".",
                    "PS",
                    pastaSkilletItems,
	                    List.of("balanced"),
	                    List.of(
	                            "Boil the pasta or noodles in salted water until just tender, then save a small cup of cooking water.",
	                            "Warm oil or sauce in a skillet over medium heat until it starts to smell aromatic.",
	                            "Add the protein or vegetables and cook until warmed through and lightly coated.",
	                            "Toss in the drained pasta with a splash of cooking water so the sauce clings instead of sitting at the bottom.",
	                            "Let it bubble for one minute, then taste and finish with pepper, cheese, herbs, or extra sauce if available.")));
        }

        List<PantryItem> toastItems = distinctItems(
                findFirstByKeywords(usableItems, "bread", "toast", "bagel", "bun").orElse(null),
                findFirstByKeywords(usableItems, "egg", "tuna", "avocado", "cheese", "tofu").orElse(null),
                findFirstByKeywords(usableItems, "tomato", "salad", "cucumber", "pepper", "vegetable").orElse(null));
        if (toastItems.size() >= 2) {
            recipes.add(dynamicRecipe(
                    "dynamic-smart-toast",
                    "Smart Pantry Toast",
                    "A quick toast or sandwich idea using " + joinItemNames(toastItems) + ".",
                    "ST",
                    toastItems,
	                    List.of("low_calorie", "balanced"),
	                    List.of(
	                            "Toast the bread until the surface is crisp enough to hold toppings without getting soggy.",
	                            "Prepare the main topping: mash avocado, flake tuna, slice tofu, melt cheese, or cook the egg.",
	                            "Cut any vegetables thinly and pat wet ingredients dry so the toast keeps its crunch.",
	                            "Spread the soft topping first, then add protein and vegetables in layers.",
	                            "Finish with salt, pepper, chili flakes, lemon, or a tiny drizzle of oil if you have any.")));
        }

        List<PantryItem> breakfastItems = distinctItems(
                findFirstByKeywords(usableItems, "yogurt", "milk").orElse(null),
                findFirstByKeywords(usableItems, "oat", "muesli", "granola").orElse(null),
                findFirstByKeywords(usableItems, "berry", "banana", "apple", "fruit").orElse(null));
        if (breakfastItems.size() >= 2) {
            recipes.add(dynamicRecipe(
                    "dynamic-breakfast-bowl",
                    "Breakfast Pantry Bowl",
                    "A no-fuss breakfast bowl using " + joinItemNames(breakfastItems) + ".",
                    "BB",
                    breakfastItems,
	                    List.of("low_calorie", "balanced"),
	                    List.of(
	                            "Spoon the creamy base into a bowl and stir it until smooth.",
	                            "Add oats, muesli, or granola around the edge so part stays crunchy.",
	                            "Slice fruit into small pieces and scatter it over the top.",
	                            "Let the bowl sit for five minutes if you want softer oats, or eat immediately for more texture.",
	                            "Finish with cinnamon, a pinch of salt, or a small splash of milk if the bowl feels too thick.")));
        }

        List<PantryItem> soupItems = distinctItems(
                findFirstByKeywords(usableItems, "lentil", "bean", "chickpea").orElse(null),
                findFirstByKeywords(usableItems, "tomato", "sauce", "carrot", "vegetable").orElse(null),
                findFirstByKeywords(usableItems, "rice", "pasta", "potato").orElse(null),
                findFirstByKeywords(usableItems, "oil").orElse(null));
        if (soupItems.size() >= 2) {
            recipes.add(dynamicRecipe(
                    "dynamic-pantry-soup",
                    "Pantry Soup",
                    "A cozy soup idea based on " + joinItemNames(soupItems) + ".",
                    "SO",
                    soupItems,
	                    List.of("low_calorie", "vegetarian", "balanced"),
	                    List.of(
	                            "Cut vegetables small so the soup cooks quickly and evenly.",
	                            "Warm oil in a pot, then stir the vegetables or legumes for a minute to build flavor.",
	                            "Add water or broth until the ingredients are just covered, then bring everything to a gentle simmer.",
	                            "Cook until lentils, beans, rice, pasta, or potatoes are tender and the soup thickens slightly.",
	                            "Taste, season, and add a splash of water if it is too thick before serving warm.")));
        }

        List<PantryItem> anyAvailableItems = usableItems.stream()
                .sorted(Comparator
                        .comparing((PantryItem item) -> caloriesFor(item, defaultRecipeAmount(item)), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(PantryItem::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .limit(3)
                .toList();
        if (anyAvailableItems.size() >= 2) {
            recipes.add(dynamicRecipe(
                    "dynamic-pantry-freestyle",
                    "Pantry Freestyle",
                    "A flexible cook-now idea that directly uses " + joinItemNames(anyAvailableItems) + ".",
                    "PF",
                    anyAvailableItems,
	                    List.of("balanced"),
	                    List.of(
	                            "Check the pantry impact amounts first and decide which ingredient should be the base.",
	                            "Cook the ingredient that takes longest before touching the faster toppings.",
	                            "Warm or crisp the remaining ingredients in the same pan so they pick up flavor from each other.",
	                            "Combine everything only at the end to avoid turning the dish mushy.",
	                            "Taste and adjust with any available seasoning, sauce, acid, or oil, then plate it as a bowl, toast, wrap, or salad.")));
        }

        return recipes.stream()
                .limit(7)
                .toList();
    }

    private Recipe dynamicRecipe(
            String id,
            String title,
            String summary,
            String imageToken,
            List<PantryItem> items,
            List<String> tags,
            List<String> instructions) {
        List<RecipeIngredient> ingredients = items.stream()
                .map(item -> ingredient(
                        item.getName(),
                        defaultRecipeAmount(item),
                        item.getAmountUnit(),
                        item.getName()))
                .toList();
        double totalCalories = items.stream()
                .mapToDouble(item -> caloriesFor(item, defaultRecipeAmount(item)))
                .sum();
        double caloriesPerServing = totalCalories > 0 ? round(totalCalories / 2.0) : 420.0;
        boolean highProtein = tags.contains("high_protein");
        boolean lowCalorie = tags.contains("low_calorie");
        return new Recipe(
                dynamicRecipeId(id, items),
                title,
                summary,
                imageToken,
                2,
                caloriesPerServing,
                highProtein ? 30.0 : 18.0,
                lowCalorie ? 36.0 : 52.0,
                lowCalorie ? 10.0 : 16.0,
                tags,
                ingredients,
                instructions);
    }

    private String dynamicRecipeId(String baseId, List<PantryItem> items) {
        String itemFingerprint = items.stream()
                .map(PantryItem::getId)
                .map(String::valueOf)
                .collect(Collectors.joining("-"));
        return baseId + "-" + itemFingerprint;
    }

    private Optional<PantryItem> findFirstByKeywords(List<PantryItem> items, String... keywords) {
        return items.stream()
                .filter(item -> matchesName(item.getName(), List.of(keywords)))
                .findFirst();
    }

    private List<PantryItem> distinctItems(PantryItem... items) {
        Set<Long> seenIds = new HashSet<>();
        List<PantryItem> distinct = new ArrayList<>();
        for (PantryItem item : items) {
            if (item == null || item.getId() == null || seenIds.contains(item.getId())) {
                continue;
            }
            seenIds.add(item.getId());
            distinct.add(item);
        }
        return distinct;
    }

    private String joinItemNames(List<PantryItem> items) {
        return items.stream()
                .map(PantryItem::getName)
                .limit(3)
                .collect(Collectors.joining(", "));
    }

    private double defaultRecipeAmount(PantryItem item) {
        double available = safeAmount(item.getAmount());
        String name = normalize(item.getName());
        return switch (item.getAmountUnit()) {
            case "g" -> Math.max(1.0, Math.min(available, defaultGramAmount(name)));
            case "ml" -> Math.max(1.0, Math.min(available, defaultMlAmount(name)));
            default -> Math.max(0.01, Math.min(available, defaultPackageAmount(name)));
        };
    }

    private double defaultGramAmount(String normalizedName) {
        if (normalizedName.contains("rice") || normalizedName.contains("pasta") || normalizedName.contains("oat")) {
            return 120.0;
        }
        if (normalizedName.contains("chicken") || normalizedName.contains("tofu") || normalizedName.contains("tuna")) {
            return 180.0;
        }
        return 150.0;
    }

    private double defaultMlAmount(String normalizedName) {
        if (normalizedName.contains("oil") || normalizedName.contains("sauce")) {
            return 15.0;
        }
        return 120.0;
    }

    private double defaultPackageAmount(String normalizedName) {
        if (normalizedName.contains("egg")) {
            return 0.17;
        }
        if (normalizedName.contains("bread") || normalizedName.contains("toast")) {
            return 0.2;
        }
        return 1.0;
    }

    private double caloriesFor(PantryItem item, double amount) {
        if ("g".equals(item.getAmountUnit()) && item.getKcalPer100g() != null) {
            return item.getKcalPer100g() * amount / 100.0;
        }
        if ("ml".equals(item.getAmountUnit()) && item.getKcalPer100ml() != null) {
            return item.getKcalPer100ml() * amount / 100.0;
        }
        if ("package".equals(item.getAmountUnit()) && item.getKcalPerPackage() != null) {
            return item.getKcalPerPackage() * amount;
        }
        return 0.0;
    }

    private List<Recipe> recipeCatalog() {
        return List.of(
                new Recipe(
                        "chicken-rice-power-bowl",
                        "Chicken Rice Power Bowl",
                        "A balanced high-protein bowl using simple pantry staples.",
                        "bowl",
                        2,
                        520.0,
                        38.0,
                        58.0,
                        16.0,
                        List.of("balanced", "high_protein"),
                        List.of(
                                ingredient("Chicken breast", 300.0, "g", "chicken", "chicken breast"),
                                ingredient("Rice", 180.0, "g", "rice"),
                                ingredient("Broccoli", 200.0, "g", "broccoli"),
                                ingredient("Olive oil", 20.0, "ml", "olive oil", "oil")),
	                        List.of(
	                                "Rinse the rice, then cook it until tender and let it rest covered for a few minutes.",
	                                "Cut the chicken into even strips and season with salt, pepper, or any spice mix you have.",
	                                "Sear the chicken in a hot pan until golden outside and fully cooked through.",
	                                "Steam or pan-cook the broccoli until bright green and still slightly crisp.",
	                                "Build the bowl with rice first, add chicken and broccoli, then finish with olive oil and a final pinch of seasoning.")),
                new Recipe(
                        "tomato-pasta-light",
                        "Light Tomato Pasta",
                        "A simple lower-calorie pasta plate with a bright tomato sauce.",
                        "pasta",
                        2,
                        430.0,
                        16.0,
                        72.0,
                        9.0,
                        List.of("balanced", "low_calorie"),
                        List.of(
                                ingredient("Pasta", 180.0, "g", "pasta", "spaghetti", "penne"),
                                ingredient("Tomato sauce", 250.0, "g", "tomato", "tomato sauce"),
                                ingredient("Olive oil", 10.0, "ml", "olive oil", "oil")),
	                        List.of(
	                                "Bring a pot of salted water to a boil and cook the pasta until al dente.",
	                                "Save a small cup of pasta water before draining.",
	                                "Warm olive oil in a pan, add the tomato sauce, and let it simmer until slightly thicker.",
	                                "Toss the pasta through the sauce with a splash of pasta water until glossy.",
	                                "Serve warm with black pepper, herbs, or a small amount of cheese if available.")),
                new Recipe(
                        "tofu-veggie-stir-fry",
                        "Tofu Veggie Stir Fry",
                        "A plant-forward protein dinner that works well with rice or noodles.",
                        "stir-fry",
                        2,
                        480.0,
                        28.0,
                        46.0,
                        18.0,
                        List.of("balanced", "high_protein", "vegetarian"),
                        List.of(
                                ingredient("Tofu", 300.0, "g", "tofu"),
                                ingredient("Mixed vegetables", 300.0, "g", "vegetable", "broccoli", "carrot", "pepper"),
                                ingredient("Rice", 120.0, "g", "rice"),
                                ingredient("Soy sauce", 20.0, "ml", "soy sauce", "soya sauce")),
	                        List.of(
	                                "Press the tofu with kitchen paper for a few minutes, then cut it into cubes.",
	                                "Cook the rice first and keep it warm while you prepare the stir-fry.",
	                                "Sear tofu in a hot pan until the edges are golden and slightly crisp.",
	                                "Add vegetables and stir-fry quickly so they soften but keep some bite.",
	                                "Pour in soy sauce, toss until coated, and serve everything over rice.")),
                new Recipe(
                        "egg-avocado-toast",
                        "Egg Avocado Toast",
                        "A quick protein-rich breakfast or lunch with pantry-friendly portions.",
                        "toast",
                        2,
                        390.0,
                        18.0,
                        32.0,
                        22.0,
                        List.of("balanced", "low_calorie"),
                        List.of(
                                ingredient("Eggs", 0.17, "package", "egg", "eggs"),
                                ingredient("Bread", 0.2, "package", "bread", "toast"),
                                ingredient("Avocado", 1.0, "package", "avocado")),
	                        List.of(
	                                "Toast the bread until the edges are crisp and the center is sturdy.",
	                                "Halve the avocado, mash it with a fork, and season with salt, pepper, or lemon if you have it.",
	                                "Cook the eggs to your preference: fried for richness, boiled for meal prep, or scrambled for speed.",
	                                "Spread avocado over the toast while it is still warm, then place the eggs on top.",
	                                "Finish with pepper, chili flakes, or a tiny drizzle of oil and serve before the toast softens.")),
                new Recipe(
                        "yogurt-berry-bowl",
                        "Yogurt Berry Bowl",
                        "A light no-cook option for a fast snack or breakfast.",
                        "yogurt",
                        1,
                        310.0,
                        21.0,
                        42.0,
                        6.0,
                        List.of("low_calorie", "high_protein"),
                        List.of(
                                ingredient("Greek yogurt", 200.0, "g", "greek yogurt", "yogurt"),
                                ingredient("Berries", 120.0, "g", "berries", "berry", "strawberry", "blueberry"),
                                ingredient("Oats", 30.0, "g", "oats", "oat")),
	                        List.of(
	                                "Spoon the yogurt into a bowl and stir it until creamy.",
	                                "Rinse or slice the berries, then scatter them over the yogurt.",
	                                "Sprinkle oats on top, keeping some at the edge for crunch.",
	                                "Let the bowl sit for five minutes if you prefer softer oats.",
	                                "Finish with cinnamon, a small spoon of honey, or extra fruit if available.")),
                new Recipe(
                        "lentil-soup",
                        "Cozy Lentil Soup",
                        "A filling high-fiber soup for batch cooking.",
                        "soup",
                        3,
                        360.0,
                        22.0,
                        54.0,
                        7.0,
                        List.of("balanced", "low_calorie", "vegetarian"),
                        List.of(
                                ingredient("Lentils", 240.0, "g", "lentil", "lentils"),
                                ingredient("Carrots", 160.0, "g", "carrot", "carrots"),
                                ingredient("Tomato sauce", 200.0, "g", "tomato", "tomato sauce"),
                                ingredient("Olive oil", 10.0, "ml", "olive oil", "oil")),
	                        List.of(
	                                "Rinse the lentils and cut the carrots into small cubes so they cook evenly.",
	                                "Warm olive oil in a pot, then stir the carrots for two minutes to build sweetness.",
	                                "Add lentils, tomato sauce, and enough water or broth to cover everything well.",
	                                "Simmer until the lentils are tender and the soup becomes naturally thick.",
	                                "Season at the end, then add a splash of water if you want a lighter soup.")),
                new Recipe(
                        "tuna-rice-salad",
                        "Tuna Rice Salad",
                        "A fast high-protein meal with a fresh pantry-friendly base.",
                        "salad",
                        2,
                        460.0,
                        34.0,
                        48.0,
                        13.0,
                        List.of("high_protein", "balanced"),
                        List.of(
                                ingredient("Tuna", 1.0, "package", "tuna"),
                                ingredient("Rice", 140.0, "g", "rice"),
                                ingredient("Mixed vegetables", 200.0, "g", "vegetable", "corn", "pepper", "cucumber"),
                                ingredient("Olive oil", 10.0, "ml", "olive oil", "oil")),
	                        List.of(
	                                "Cook the rice until tender, then spread it out for a few minutes so it cools slightly.",
	                                "Drain the tuna and flake it with a fork.",
	                                "Chop the vegetables into small pieces so they mix evenly through the rice.",
	                                "Toss rice, tuna, vegetables, and olive oil together until lightly coated.",
	                                "Taste and brighten it with pepper, lemon, vinegar, or herbs if you have them.")),
                new Recipe(
                        "banana-oat-pancakes",
                        "Banana Oat Pancakes",
                        "A gentle breakfast option using just a few common pantry items.",
                        "pancakes",
                        2,
                        410.0,
                        17.0,
                        62.0,
                        11.0,
                        List.of("balanced", "vegetarian"),
                        List.of(
                                ingredient("Banana", 2.0, "package", "banana", "bananas"),
                                ingredient("Oats", 100.0, "g", "oats", "oat"),
                                ingredient("Eggs", 0.17, "package", "egg", "eggs"),
                                ingredient("Milk", 120.0, "ml", "milk")),
		                        List.of(
		                                "Mash the bananas in a bowl until mostly smooth.",
		                                "Stir in oats, eggs, and milk, then let the batter rest for five minutes so the oats soften.",
		                                "Heat a non-stick pan over medium heat and lightly oil it if needed.",
		                                "Cook small pancakes until bubbles appear, then flip and cook the second side until golden.",
		                                "Serve warm with fruit, yogurt, or a small drizzle of honey if available.")),
	                new Recipe(
	                        "veggie-fried-rice",
	                        "Veggie Fried Rice",
	                        "A practical leftover-style rice dish with eggs and vegetables.",
	                        "fried-rice",
	                        2,
	                        470.0,
	                        19.0,
	                        63.0,
	                        15.0,
	                        List.of("balanced", "vegetarian"),
	                        List.of(
	                                ingredient("Rice", 180.0, "g", "rice"),
	                                ingredient("Eggs", 0.17, "package", "egg", "eggs"),
	                                ingredient("Mixed vegetables", 250.0, "g", "vegetable", "peas", "carrot", "corn", "pepper"),
	                                ingredient("Soy sauce", 20.0, "ml", "soy sauce", "soya sauce"),
	                                ingredient("Olive oil", 10.0, "ml", "olive oil", "oil")),
	                        List.of(
	                                "Cook the rice ahead of time if possible, or spread fresh rice out for a few minutes so it dries slightly.",
	                                "Beat the eggs and scramble them quickly in a hot pan, then move them to the side.",
	                                "Add vegetables and cook until they are hot but still colorful.",
	                                "Stir in the rice and soy sauce, breaking up clumps as the grains fry.",
	                                "Fold the eggs back in, taste, and finish with a little extra sauce or pepper if needed.")),
	                new Recipe(
	                        "chickpea-tomato-rice-bowl",
	                        "Chickpea Tomato Rice Bowl",
	                        "A filling vegetarian bowl built around beans, rice, and tomato.",
	                        "chickpea-bowl",
	                        2,
	                        510.0,
	                        22.0,
	                        78.0,
	                        12.0,
	                        List.of("balanced", "vegetarian", "high_protein"),
	                        List.of(
	                                ingredient("Chickpeas", 240.0, "g", "chickpea", "chickpeas", "bean", "beans"),
	                                ingredient("Rice", 160.0, "g", "rice"),
	                                ingredient("Tomato sauce", 220.0, "g", "tomato", "tomato sauce"),
	                                ingredient("Olive oil", 10.0, "ml", "olive oil", "oil")),
	                        List.of(
	                                "Cook the rice until tender and keep it warm.",
	                                "Warm olive oil in a pan, then add chickpeas and cook until they start to look slightly toasted.",
	                                "Pour in tomato sauce and simmer until the chickpeas are glossy and coated.",
	                                "Taste and season with salt, pepper, paprika, cumin, or chili if you have them.",
	                                "Serve the chickpeas over rice and spoon extra sauce around the bowl.")),
	                new Recipe(
	                        "potato-egg-skillet",
	                        "Potato Egg Skillet",
	                        "A hearty one-pan meal that turns potatoes and eggs into dinner.",
	                        "skillet",
	                        2,
	                        450.0,
	                        18.0,
	                        48.0,
	                        19.0,
	                        List.of("balanced", "vegetarian"),
	                        List.of(
	                                ingredient("Potatoes", 400.0, "g", "potato", "potatoes"),
	                                ingredient("Eggs", 0.17, "package", "egg", "eggs"),
	                                ingredient("Mixed vegetables", 200.0, "g", "vegetable", "tomato", "pepper", "onion"),
	                                ingredient("Olive oil", 15.0, "ml", "olive oil", "oil")),
	                        List.of(
	                                "Cut potatoes into small cubes so they cook quickly.",
	                                "Cook them in olive oil over medium heat until the edges turn golden.",
	                                "Add vegetables and cook until softened and fragrant.",
	                                "Make small wells in the skillet, crack in the eggs, and cover the pan.",
	                                "Cook until the egg whites set, then season and serve straight from the pan.")),
	                new Recipe(
	                        "tuna-tomato-pasta",
	                        "Tuna Tomato Pasta",
	                        "A high-protein pantry pasta for days when you need something reliable.",
	                        "tuna-pasta",
	                        2,
	                        500.0,
	                        31.0,
	                        69.0,
	                        11.0,
	                        List.of("balanced", "high_protein"),
	                        List.of(
	                                ingredient("Pasta", 180.0, "g", "pasta", "spaghetti", "penne"),
	                                ingredient("Tuna", 1.0, "package", "tuna"),
	                                ingredient("Tomato sauce", 220.0, "g", "tomato", "tomato sauce"),
	                                ingredient("Olive oil", 10.0, "ml", "olive oil", "oil")),
	                        List.of(
	                                "Boil the pasta until al dente and save a little cooking water.",
	                                "Warm olive oil and tomato sauce in a pan until the sauce starts to thicken.",
	                                "Drain the tuna, flake it gently, and fold it into the sauce without breaking it down too much.",
	                                "Add pasta and a splash of cooking water, then toss until the sauce coats every piece.",
	                                "Finish with pepper, herbs, chili, or lemon if you have them.")),
	                new Recipe(
	                        "chicken-veggie-toastie",
	                        "Chicken Veggie Toastie",
	                        "A warm sandwich-style meal using bread, chicken, and crisp vegetables.",
	                        "toastie",
	                        2,
	                        480.0,
	                        35.0,
	                        42.0,
	                        18.0,
	                        List.of("balanced", "high_protein"),
	                        List.of(
	                                ingredient("Chicken breast", 240.0, "g", "chicken", "chicken breast"),
	                                ingredient("Bread", 0.25, "package", "bread", "toast"),
	                                ingredient("Mixed vegetables", 180.0, "g", "vegetable", "tomato", "salad", "cucumber", "pepper"),
	                                ingredient("Cheese", 80.0, "g", "cheese")),
	                        List.of(
	                                "Cook or warm the chicken, then slice it thinly so it fits neatly on the bread.",
	                                "Toast the bread lightly before assembling so the inside does not become soggy.",
	                                "Layer chicken, vegetables, and cheese, keeping wetter vegetables away from the bottom slice.",
	                                "Press the sandwich in a pan for a few minutes until the bread is crisp and the cheese softens.",
	                                "Cut in half and let it rest for one minute before eating.")),
	                new Recipe(
	                        "bean-avocado-toast",
	                        "Bean Avocado Toast",
	                        "A fiber-rich vegetarian toast that is more filling than it looks.",
	                        "bean-toast",
	                        2,
	                        430.0,
	                        18.0,
	                        54.0,
	                        17.0,
	                        List.of("balanced", "vegetarian", "low_calorie"),
	                        List.of(
	                                ingredient("Bread", 0.2, "package", "bread", "toast"),
	                                ingredient("Beans", 220.0, "g", "bean", "beans", "chickpea", "lentil"),
	                                ingredient("Avocado", 1.0, "package", "avocado"),
	                                ingredient("Tomatoes", 160.0, "g", "tomato", "tomatoes")),
	                        List.of(
	                                "Toast the bread until crisp enough to hold a generous topping.",
	                                "Warm the beans briefly, then mash half of them with a fork for a creamy texture.",
	                                "Mash avocado separately and season it with salt, pepper, or lemon if available.",
	                                "Spread avocado on the toast, add beans, then finish with sliced tomatoes.",
	                                "Serve immediately while the toast is still crunchy.")),
	                new Recipe(
	                        "peanut-banana-oats",
	                        "Peanut Banana Oats",
	                        "A higher-energy breakfast bowl that suits muscle gain or busy mornings.",
	                        "oats",
	                        1,
	                        560.0,
	                        22.0,
	                        72.0,
	                        22.0,
	                        List.of("balanced", "high_protein"),
	                        List.of(
	                                ingredient("Oats", 70.0, "g", "oats", "oat"),
	                                ingredient("Banana", 1.0, "package", "banana", "bananas"),
	                                ingredient("Peanut butter", 30.0, "g", "peanut butter", "peanut"),
	                                ingredient("Milk", 180.0, "ml", "milk")),
	                        List.of(
	                                "Simmer oats with milk over medium heat, stirring often so they become creamy.",
	                                "Mash half the banana into the oats while they cook for natural sweetness.",
	                                "Stir in peanut butter until it melts through the bowl.",
	                                "Slice the remaining banana and place it on top.",
	                                "Let the oats cool for one minute before eating so the texture thickens.")),
	                new Recipe(
	                        "tofu-rice-soup",
	                        "Tofu Rice Soup",
	                        "A gentle warm meal with tofu, rice, and vegetables.",
	                        "tofu-soup",
	                        2,
	                        390.0,
	                        24.0,
	                        49.0,
	                        11.0,
	                        List.of("balanced", "vegetarian", "low_calorie", "high_protein"),
	                        List.of(
	                                ingredient("Tofu", 280.0, "g", "tofu"),
	                                ingredient("Rice", 100.0, "g", "rice"),
	                                ingredient("Mixed vegetables", 220.0, "g", "vegetable", "carrot", "broccoli", "pepper"),
	                                ingredient("Soy sauce", 20.0, "ml", "soy sauce", "soya sauce")),
	                        List.of(
	                                "Cook the rice until almost tender, or use already cooked rice if you have it.",
	                                "Bring water or broth to a gentle simmer and add the vegetables.",
	                                "Cut tofu into cubes and slide it into the pot carefully so it stays intact.",
	                                "Add rice and soy sauce, then simmer until everything is hot and tender.",
	                                "Taste the broth and adjust with more soy sauce, pepper, or herbs.")),
	                new Recipe(
	                        "cucumber-tuna-yogurt-toast",
	                        "Cucumber Tuna Yogurt Toast",
	                        "A fresh high-protein toast with a creamy yogurt-style tuna topping.",
	                        "tuna-toast",
	                        2,
	                        360.0,
	                        30.0,
	                        34.0,
	                        10.0,
	                        List.of("low_calorie", "high_protein"),
	                        List.of(
	                                ingredient("Tuna", 1.0, "package", "tuna"),
	                                ingredient("Greek yogurt", 120.0, "g", "greek yogurt", "yogurt"),
	                                ingredient("Bread", 0.2, "package", "bread", "toast"),
	                                ingredient("Cucumber", 160.0, "g", "cucumber")),
	                        List.of(
	                                "Drain the tuna well so the topping does not become watery.",
	                                "Mix tuna with Greek yogurt until creamy, then season with pepper or lemon if available.",
	                                "Toast the bread and let it cool for a few seconds so it stays crisp.",
	                                "Slice cucumber thinly and pat it dry.",
	                                "Spread tuna yogurt mixture over the toast and layer cucumber on top.")),
	                new Recipe(
	                        "protein-oat-yogurt-bowl",
	                        "Protein Oat Yogurt Bowl",
	                        "A cool high-protein bowl for breakfast, snack, or after training.",
	                        "protein-bowl",
	                        1,
	                        430.0,
	                        30.0,
	                        56.0,
	                        9.0,
	                        List.of("high_protein", "balanced"),
	                        List.of(
	                                ingredient("Greek yogurt", 220.0, "g", "greek yogurt", "yogurt"),
	                                ingredient("Oats", 45.0, "g", "oats", "oat"),
	                                ingredient("Milk", 80.0, "ml", "milk"),
	                                ingredient("Banana", 1.0, "package", "banana", "berries", "fruit")),
	                        List.of(
	                                "Stir Greek yogurt and milk together until smooth.",
	                                "Fold in oats and let the bowl sit for five to ten minutes.",
	                                "Slice the banana or fruit into bite-size pieces.",
	                                "Top the yogurt oat base with fruit and press some pieces slightly into the bowl.",
	                                "Serve cold, or chill it longer if you prefer an overnight-oats texture.")));
    }

    private RecipeIngredient ingredient(String name, Double amount, String unit, String... keywords) {
        Set<String> allKeywords = new HashSet<>(List.of(keywords));
        allKeywords.add(name);
        return new RecipeIngredient(name, amount, unit, List.copyOf(allKeywords));
    }

    private record Recipe(
            String id,
            String title,
            String summary,
            String imageToken,
            int servings,
            Double caloriesPerServing,
            Double proteinGrams,
            Double carbsGrams,
            Double fatGrams,
            List<String> tags,
            List<RecipeIngredient> ingredients,
            List<String> instructions) {
    }

    private record RecipeIngredient(
            String name,
            Double amount,
            String unit,
            List<String> keywords) {
    }

    private record HouseholdGoalProfile(Map<String, Long> goalCounts, double averageDailyCalories) {
        static HouseholdGoalProfile empty() {
            return new HouseholdGoalProfile(Map.of(), 0.0);
        }

        static HouseholdGoalProfile from(List<UserHealthGoal> goals) {
            if (goals == null || goals.isEmpty()) {
                return empty();
            }
            Map<String, Long> counts = goals.stream()
                    .collect(Collectors.groupingBy(UserHealthGoal::getGoalType, Collectors.counting()));
            double averageCalories = goals.stream()
                    .map(UserHealthGoal::getRecommendedDailyCalories)
                    .filter(value -> value != null && value > 0)
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
            return new HouseholdGoalProfile(counts, averageCalories);
        }

        int score(Recipe recipe) {
            if (goalCounts.isEmpty()) {
                return 5;
            }
            int score = 0;
            score += goalCounts.getOrDefault("LOSE_WEIGHT", 0L) * weightLossScore(recipe);
            score += goalCounts.getOrDefault("MAINTAIN", 0L) * maintainScore(recipe);
            score += goalCounts.getOrDefault("GAIN_MUSCLE", 0L) * muscleGainScore(recipe);
            if (averageDailyCalories > 0 && recipe.caloriesPerServing() <= averageDailyCalories * 0.35) {
                score += 4;
            }
            return Math.min(38, score);
        }

        String fitLabel(Recipe recipe) {
            if (goalCounts.isEmpty()) {
                return "Pantry-based suggestion; no household health goals are set yet";
            }
            String dominantGoal = goalCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("MAINTAIN");
            return switch (dominantGoal) {
                case "LOSE_WEIGHT" -> recipe.tags().contains("low_calorie")
                        ? "Fits the household's lighter calorie goal"
                        : "Usable for weight goals with portion awareness";
                case "GAIN_MUSCLE" -> recipe.tags().contains("high_protein")
                        ? "Fits the household's high-protein goal"
                        : "Balanced option for mixed household goals";
                default -> recipe.tags().contains("balanced")
                        ? "Fits the household's balanced maintenance goal"
                        : "Useful pantry match for the household";
            };
        }

        private int weightLossScore(Recipe recipe) {
            int score = recipe.tags().contains("low_calorie") ? 13 : 2;
            if (recipe.caloriesPerServing() <= 450.0) {
                score += 6;
            }
            if (recipe.proteinGrams() >= 20.0) {
                score += 2;
            }
            if (recipe.caloriesPerServing() > 550.0) {
                score -= 4;
            }
            return score;
        }

        private int maintainScore(Recipe recipe) {
            int score = recipe.tags().contains("balanced") ? 12 : 5;
            if (recipe.caloriesPerServing() >= 350.0 && recipe.caloriesPerServing() <= 600.0) {
                score += 4;
            }
            return score;
        }

        private int muscleGainScore(Recipe recipe) {
            int score = recipe.tags().contains("high_protein") ? 14 : 3;
            if (recipe.proteinGrams() >= 25.0) {
                score += 7;
            }
            if (recipe.caloriesPerServing() >= 450.0) {
                score += 3;
            }
            return score;
        }
    }
}
