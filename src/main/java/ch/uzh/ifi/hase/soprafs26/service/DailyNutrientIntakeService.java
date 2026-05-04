package ch.uzh.ifi.hase.soprafs26.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.ifi.hase.soprafs26.entity.DailyNutrientIntake;
import ch.uzh.ifi.hase.soprafs26.entity.PantryItem;
import ch.uzh.ifi.hase.soprafs26.entity.PantryItemMicronutrients;
import ch.uzh.ifi.hase.soprafs26.repository.DailyNutrientIntakeRepository;
import ch.uzh.ifi.hase.soprafs26.repository.PantryItemMicronutrientsRepository;

@Service
@Transactional
public class DailyNutrientIntakeService {

    private static final ZoneId APPLICATION_ZONE = ZoneId.of("Europe/Zurich");
    private static final int NUTRIENT_SCALE = 6;

    private final DailyNutrientIntakeRepository dailyNutrientIntakeRepository;
    private final PantryItemMicronutrientsRepository pantryItemMicronutrientsRepository;

    public DailyNutrientIntakeService(
            DailyNutrientIntakeRepository dailyNutrientIntakeRepository,
            PantryItemMicronutrientsRepository pantryItemMicronutrientsRepository
    ) {
        this.dailyNutrientIntakeRepository = dailyNutrientIntakeRepository;
        this.pantryItemMicronutrientsRepository = pantryItemMicronutrientsRepository;
    }

    public DailyNutrientIntake getDailyIntakeOrEmpty(Long userId, LocalDate intakeDate) {
        LocalDate resolvedDate = intakeDate == null ? LocalDate.now(APPLICATION_ZONE) : intakeDate;
        return dailyNutrientIntakeRepository
                .findByUserIdAndIntakeDate(userId, resolvedDate)
                .orElseGet(() -> createEmptyDailyIntake(userId, resolvedDate));
    }

    public DailyNutrientIntake recordConsumedPantryItem(
            Long userId,
            PantryItem pantryItem,
            Integer consumedQuantity,
            Instant consumedAt
    ) {
        if (userId == null || pantryItem == null || pantryItem.getId() == null || consumedQuantity == null || consumedQuantity <= 0) {
            return null;
        }

        PantryItemMicronutrients pantryItemMicronutrients = pantryItemMicronutrientsRepository
                .findByPantryItemId(pantryItem.getId())
                .orElse(null);
        if (pantryItemMicronutrients == null) {
            return null;
        }

        Instant resolvedConsumedAt = consumedAt == null ? Instant.now() : consumedAt;
        LocalDate intakeDate = LocalDate.ofInstant(resolvedConsumedAt, APPLICATION_ZONE);
        BigDecimal multiplier = BigDecimal.valueOf(consumedQuantity.longValue());

        DailyNutrientIntake dailyIntake = dailyNutrientIntakeRepository
                .findByUserIdAndIntakeDate(userId, intakeDate)
                .orElseGet(() -> createEmptyDailyIntake(userId, intakeDate));

        addConsumedMicronutrients(dailyIntake, pantryItemMicronutrients, multiplier);
        return dailyNutrientIntakeRepository.save(dailyIntake);
    }

    private DailyNutrientIntake createEmptyDailyIntake(Long userId, LocalDate intakeDate) {
        DailyNutrientIntake dailyIntake = new DailyNutrientIntake();
        dailyIntake.setUserId(userId);
        dailyIntake.setIntakeDate(intakeDate);
        return dailyIntake;
    }

    private void addConsumedMicronutrients(
            DailyNutrientIntake dailyIntake,
            PantryItemMicronutrients pantryItemMicronutrients,
            BigDecimal multiplier
    ) {
        dailyIntake.setBiotin(add(dailyIntake.getBiotin(), pantryItemMicronutrients.getBiotin(), multiplier));
        dailyIntake.setCalcium(add(dailyIntake.getCalcium(), pantryItemMicronutrients.getCalcium(), multiplier));
        dailyIntake.setChloride(add(dailyIntake.getChloride(), pantryItemMicronutrients.getChloride(), multiplier));
        dailyIntake.setCholine(add(dailyIntake.getCholine(), pantryItemMicronutrients.getCholine(), multiplier));
        dailyIntake.setChromium(add(dailyIntake.getChromium(), pantryItemMicronutrients.getChromium(), multiplier));
        dailyIntake.setCopper(add(dailyIntake.getCopper(), pantryItemMicronutrients.getCopper(), multiplier));
        dailyIntake.setFluoride(add(dailyIntake.getFluoride(), pantryItemMicronutrients.getFluoride(), multiplier));
        dailyIntake.setFolate(add(dailyIntake.getFolate(), pantryItemMicronutrients.getFolate(), multiplier));
        dailyIntake.setIodine(add(dailyIntake.getIodine(), pantryItemMicronutrients.getIodine(), multiplier));
        dailyIntake.setIron(add(dailyIntake.getIron(), pantryItemMicronutrients.getIron(), multiplier));
        dailyIntake.setMagnesium(add(dailyIntake.getMagnesium(), pantryItemMicronutrients.getMagnesium(), multiplier));
        dailyIntake.setManganese(add(dailyIntake.getManganese(), pantryItemMicronutrients.getManganese(), multiplier));
        dailyIntake.setMolybdenum(add(dailyIntake.getMolybdenum(), pantryItemMicronutrients.getMolybdenum(), multiplier));
        dailyIntake.setNiacin(add(dailyIntake.getNiacin(), pantryItemMicronutrients.getNiacin(), multiplier));
        dailyIntake.setPantothenicAcid(add(dailyIntake.getPantothenicAcid(), pantryItemMicronutrients.getPantothenicAcid(), multiplier));
        dailyIntake.setPhosphorus(add(dailyIntake.getPhosphorus(), pantryItemMicronutrients.getPhosphorus(), multiplier));
        dailyIntake.setPotassium(add(dailyIntake.getPotassium(), pantryItemMicronutrients.getPotassium(), multiplier));
        dailyIntake.setRiboflavin(add(dailyIntake.getRiboflavin(), pantryItemMicronutrients.getRiboflavin(), multiplier));
        dailyIntake.setSelenium(add(dailyIntake.getSelenium(), pantryItemMicronutrients.getSelenium(), multiplier));
        dailyIntake.setSodium(add(dailyIntake.getSodium(), pantryItemMicronutrients.getSodium(), multiplier));
        dailyIntake.setThiamin(add(dailyIntake.getThiamin(), pantryItemMicronutrients.getThiamin(), multiplier));
        dailyIntake.setVitaminA(add(dailyIntake.getVitaminA(), pantryItemMicronutrients.getVitaminA(), multiplier));
        dailyIntake.setVitaminB12(add(dailyIntake.getVitaminB12(), pantryItemMicronutrients.getVitaminB12(), multiplier));
        dailyIntake.setVitaminB6(add(dailyIntake.getVitaminB6(), pantryItemMicronutrients.getVitaminB6(), multiplier));
        dailyIntake.setVitaminC(add(dailyIntake.getVitaminC(), pantryItemMicronutrients.getVitaminC(), multiplier));
        dailyIntake.setVitaminD(add(dailyIntake.getVitaminD(), pantryItemMicronutrients.getVitaminD(), multiplier));
        dailyIntake.setVitaminE(add(dailyIntake.getVitaminE(), pantryItemMicronutrients.getVitaminE(), multiplier));
        dailyIntake.setVitaminK(add(dailyIntake.getVitaminK(), pantryItemMicronutrients.getVitaminK(), multiplier));
        dailyIntake.setZinc(add(dailyIntake.getZinc(), pantryItemMicronutrients.getZinc(), multiplier));
    }

    private BigDecimal add(BigDecimal currentValue, BigDecimal consumedPackageValue, BigDecimal multiplier) {
        if (consumedPackageValue == null || multiplier == null) {
            return currentValue;
        }

        BigDecimal baseValue = currentValue == null ? BigDecimal.ZERO : currentValue;
        return baseValue
                .add(consumedPackageValue.multiply(multiplier))
                .setScale(NUTRIENT_SCALE, RoundingMode.HALF_UP);
    }
}
