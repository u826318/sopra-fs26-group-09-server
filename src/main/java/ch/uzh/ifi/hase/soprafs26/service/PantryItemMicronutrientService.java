package ch.uzh.ifi.hase.soprafs26.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.ifi.hase.soprafs26.entity.PantryItem;
import ch.uzh.ifi.hase.soprafs26.entity.PantryItemMicronutrients;
import ch.uzh.ifi.hase.soprafs26.repository.PantryItemMicronutrientsRepository;

@Service
@Transactional
public class PantryItemMicronutrientService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MICROGRAMS_PER_GRAM = BigDecimal.valueOf(1_000_000);
    private static final BigDecimal MICROGRAMS_PER_MILLIGRAM = BigDecimal.valueOf(1_000);

    private final PantryItemMicronutrientsRepository pantryItemMicronutrientsRepository;

    public PantryItemMicronutrientService(PantryItemMicronutrientsRepository pantryItemMicronutrientsRepository) {
        this.pantryItemMicronutrientsRepository = pantryItemMicronutrientsRepository;
    }

    public void upsertMicronutrientsPerPackage(
            PantryItem pantryItem,
            String packageQuantity,
            Map<String, Object> nutriments
    ) {
        if (pantryItem == null || pantryItem.getId() == null || nutriments == null || nutriments.isEmpty()) {
            return;
        }

        BigDecimal packageGrams = parsePackageGrams(packageQuantity);
        if (packageGrams == null || packageGrams.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        PantryItemMicronutrients micronutrients = pantryItemMicronutrientsRepository
                .findByPantryItemId(pantryItem.getId())
                .orElseGet(PantryItemMicronutrients::new);

        micronutrients.setPantryItem(pantryItem);
        micronutrients.setPackageQuantity(packageQuantity);
        micronutrients.setPackageGrams(packageGrams);

        micronutrients.setBiotin(calculatePackageAmount(nutriments, packageGrams, "biotin"));
        micronutrients.setCalcium(calculatePackageAmount(nutriments, packageGrams, "calcium"));
        micronutrients.setChloride(calculatePackageAmount(nutriments, packageGrams, "chloride"));
        micronutrients.setCholine(calculatePackageAmount(nutriments, packageGrams, "choline"));
        micronutrients.setChromium(calculatePackageAmount(nutriments, packageGrams, "chromium"));
        micronutrients.setCopper(calculatePackageAmount(nutriments, packageGrams, "copper"));
        micronutrients.setFluoride(calculatePackageAmount(nutriments, packageGrams, "fluoride"));
        micronutrients.setFolate(calculatePackageAmount(nutriments, packageGrams, "vitamin-b9", "folates"));
        micronutrients.setIodine(calculatePackageAmount(nutriments, packageGrams, "iodine"));
        micronutrients.setIron(calculatePackageAmount(nutriments, packageGrams, "iron"));
        micronutrients.setMagnesium(calculatePackageAmount(nutriments, packageGrams, "magnesium"));
        micronutrients.setManganese(calculatePackageAmount(nutriments, packageGrams, "manganese"));
        micronutrients.setMolybdenum(calculatePackageAmount(nutriments, packageGrams, "molybdenum"));
        micronutrients.setNiacin(calculatePackageAmount(nutriments, packageGrams, "vitamin-pp"));
        micronutrients.setPantothenicAcid(calculatePackageAmount(nutriments, packageGrams, "pantothenic-acid"));
        micronutrients.setPhosphorus(calculatePackageAmount(nutriments, packageGrams, "phosphorus"));
        micronutrients.setPotassium(calculatePackageAmount(nutriments, packageGrams, "potassium"));
        micronutrients.setRiboflavin(calculatePackageAmount(nutriments, packageGrams, "vitamin-b2"));
        micronutrients.setSelenium(calculatePackageAmount(nutriments, packageGrams, "selenium"));
        micronutrients.setSodium(calculatePackageAmount(nutriments, packageGrams, "sodium"));
        micronutrients.setThiamin(calculatePackageAmount(nutriments, packageGrams, "vitamin-b1"));
        micronutrients.setVitaminA(calculatePackageAmount(nutriments, packageGrams, "vitamin-a"));
        micronutrients.setVitaminB12(calculatePackageAmount(nutriments, packageGrams, "vitamin-b12"));
        micronutrients.setVitaminB6(calculatePackageAmount(nutriments, packageGrams, "vitamin-b6"));
        micronutrients.setVitaminC(calculatePackageAmount(nutriments, packageGrams, "vitamin-c"));
        micronutrients.setVitaminD(calculatePackageAmount(nutriments, packageGrams, "vitamin-d"));
        micronutrients.setVitaminE(calculatePackageAmount(nutriments, packageGrams, "vitamin-e"));
        micronutrients.setVitaminK(calculatePackageAmount(nutriments, packageGrams, "vitamin-k", "phylloquinone"));
        micronutrients.setZinc(calculatePackageAmount(nutriments, packageGrams, "zinc"));

        pantryItem.setMicronutrients(micronutrients);
        pantryItemMicronutrientsRepository.save(micronutrients);
    }

    private BigDecimal calculatePackageAmount(
            Map<String, Object> nutriments,
            BigDecimal packageGrams,
            String... offBaseKeys
    ) {
        BigDecimal per100gMicrograms = findPer100gMicrograms(nutriments, offBaseKeys);
        if (per100gMicrograms == null) {
            return null;
        }

        return per100gMicrograms
                .multiply(packageGrams)
                .divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal findPer100gMicrograms(Map<String, Object> nutriments, String... offBaseKeys) {
        for (String offBaseKey : offBaseKeys) {
            BigDecimal normalizedPer100g = parseBigDecimalOrNull(nutriments.get(offBaseKey + "_100g"));
            if (normalizedPer100g != null) {
                return normalizedPer100g.multiply(MICROGRAMS_PER_GRAM);
            }

            BigDecimal explicitValue = parseBigDecimalOrNull(nutriments.get(offBaseKey + "_value"));
            String explicitUnit = parseStringOrNull(nutriments.get(offBaseKey + "_unit"));
            BigDecimal convertedExplicitValue = convertToMicrograms(explicitValue, explicitUnit);
            if (convertedExplicitValue != null) {
                return convertedExplicitValue;
            }
        }

        return null;
    }

    private BigDecimal convertToMicrograms(BigDecimal value, String unit) {
        if (value == null || unit == null) {
            return null;
        }

        String normalizedUnit = unit.trim().toLowerCase(Locale.ROOT);
        if (normalizedUnit.equals("µg") || normalizedUnit.equals("μg") || normalizedUnit.equals("ug") || normalizedUnit.equals("mcg")) {
            return value;
        }
        if (normalizedUnit.equals("mg")) {
            return value.multiply(MICROGRAMS_PER_MILLIGRAM);
        }
        if (normalizedUnit.equals("g")) {
            return value.multiply(MICROGRAMS_PER_GRAM);
        }

        return null;
    }

    private BigDecimal parsePackageGrams(String packageQuantity) {
        String text = parseStringOrNull(packageQuantity);
        if (text == null) {
            return null;
        }

        String normalizedText = text.toLowerCase(Locale.ROOT).replace(',', '.').trim();

        MultipliedQuantity multipliedQuantity = parseMultipliedQuantity(normalizedText);
        if (multipliedQuantity != null) {
            return toGrams(multipliedQuantity.totalAmount, multipliedQuantity.unit);
        }

        QuantityToken quantityToken = findQuantityToken(normalizedText, 0);
        return quantityToken == null ? null : toGrams(quantityToken.amount, quantityToken.unit);
    }

    private MultipliedQuantity parseMultipliedQuantity(String text) {
        int index = 0;
        while (index < text.length()) {
            NumberToken count = findNumberToken(text, index);
            if (count == null) {
                return null;
            }

            int cursor = skipWhitespace(text, count.endIndex);
            if (cursor < text.length() && (text.charAt(cursor) == 'x' || text.charAt(cursor) == '×')) {
                QuantityToken quantityToken = findQuantityToken(text, skipWhitespace(text, cursor + 1));
                if (quantityToken != null) {
                    return new MultipliedQuantity(count.value.multiply(quantityToken.amount), quantityToken.unit);
                }
            }

            index = Math.max(count.endIndex, index + 1);
        }
        return null;
    }

    private QuantityToken findQuantityToken(String text, int startIndex) {
        int index = Math.max(0, startIndex);
        while (index < text.length()) {
            NumberToken number = findNumberToken(text, index);
            if (number == null) {
                return null;
            }

            int unitStart = skipWhitespace(text, number.endIndex);
            String unit = readMassUnitToken(text, unitStart);
            if (unit != null) {
                return new QuantityToken(number.value, unit);
            }

            index = Math.max(number.endIndex, index + 1);
        }
        return null;
    }

    private NumberToken findNumberToken(String text, int startIndex) {
        int index = Math.max(0, startIndex);
        while (index < text.length() && !Character.isDigit(text.charAt(index))) {
            index++;
        }
        if (index >= text.length()) {
            return null;
        }

        int cursor = index;
        boolean hasDecimalSeparator = false;
        while (cursor < text.length()) {
            char current = text.charAt(cursor);
            if (Character.isDigit(current)) {
                cursor++;
                continue;
            }
            if (current == '.' && !hasDecimalSeparator) {
                hasDecimalSeparator = true;
                cursor++;
                continue;
            }
            break;
        }

        BigDecimal value = parseBigDecimalOrNull(text.substring(index, cursor));
        return value == null ? null : new NumberToken(value, cursor);
    }

    private String readMassUnitToken(String text, int startIndex) {
        String[] units = {"kg", "mg", "g"};
        for (String unit : units) {
            int endIndex = startIndex + unit.length();
            if (endIndex <= text.length()
                    && text.startsWith(unit, startIndex)
                    && isUnitBoundary(text, endIndex)) {
                return unit;
            }
        }
        return null;
    }

    private boolean isUnitBoundary(String text, int index) {
        return index >= text.length() || !Character.isLetter(text.charAt(index));
    }

    private int skipWhitespace(String text, int startIndex) {
        int index = startIndex;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private BigDecimal toGrams(BigDecimal amount, String unit) {
        if (amount == null || unit == null) {
            return null;
        }

        switch (unit) {
            case "kg":
                return amount.multiply(BigDecimal.valueOf(1000));
            case "g":
                return amount;
            case "mg":
                return amount.divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
            default:
                return null;
        }
    }

    private BigDecimal parseBigDecimalOrNull(Object value) {
        String text = parseStringOrNull(value);
        if (text == null) {
            return null;
        }

        try {
            return new BigDecimal(text.replace(',', '.'));
        }
        catch (NumberFormatException exception) {
            return null;
        }
    }

    private String parseStringOrNull(Object value) {
        if (value == null) {
            return null;
        }

        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static class NumberToken {
        private final BigDecimal value;
        private final int endIndex;

        private NumberToken(BigDecimal value, int endIndex) {
            this.value = value;
            this.endIndex = endIndex;
        }
    }

    private static class QuantityToken {
        private final BigDecimal amount;
        private final String unit;

        private QuantityToken(BigDecimal amount, String unit) {
            this.amount = amount;
            this.unit = unit;
        }
    }

    private static class MultipliedQuantity {
        private final BigDecimal totalAmount;
        private final String unit;

        private MultipliedQuantity(BigDecimal totalAmount, String unit) {
            this.totalAmount = totalAmount;
            this.unit = unit;
        }
    }
}
