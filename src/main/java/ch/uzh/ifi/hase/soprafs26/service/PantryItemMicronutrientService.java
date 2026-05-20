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
import ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductDTO;

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

    /**
     * Stores local-dataset micronutrients on their native nutrition basis, usually per 100g or 100ml.
     * Package and serving quantities are kept only as optional conversion metadata for later consumption.
     */
    public void upsertMicronutrientsPerBasisFromLocalDataset(
            PantryItem pantryItem,
            LocalDatasetProductDTO product
    ) {
        if (pantryItem == null || pantryItem.getId() == null || product == null) {
            return;
        }

        PantryItemMicronutrients micronutrients = pantryItemMicronutrientsRepository
                .findByPantryItemId(pantryItem.getId())
                .orElseGet(PantryItemMicronutrients::new);

        micronutrients.setPantryItem(pantryItem);

        BigDecimal nutritionBasisAmount = null;
        String nutritionBasisUnit = null;
        Map<String, LocalDatasetProductDTO.NutrientAmountDTO> micronutrientValuesPerBasis = null;
        if (product.getNutrition() != null) {
            nutritionBasisAmount = parseBigDecimalOrNull(product.getNutrition().getBasisAmount());
            nutritionBasisUnit = normalizeUnit(product.getNutrition().getBasisUnit());
            micronutrientValuesPerBasis = product.getNutrition().getMicronutrients();
        }

        micronutrients.setNutritionBasisAmount(nutritionBasisAmount);
        micronutrients.setNutritionBasisUnit(nutritionBasisUnit);

        BigDecimal packageQuantity = parseBigDecimalOrNull(product.getPackageQuantity());
        String packageUnit = normalizeUnit(product.getPackageQuantityUnit());
        micronutrients.setPackageQuantity(formatPackageQuantity(product.getPackageQuantity(), product.getPackageQuantityUnit()));
        micronutrients.setPackageGrams(packageQuantity); // kept for backward compatibility; may represent g or ml in new local-dataset flow
        micronutrients.setPackageQuantityValue(packageQuantity);
        micronutrients.setPackageQuantityUnit(packageUnit);

        BigDecimal servingQuantity = parseBigDecimalOrNull(product.getServingQuantity());
        String servingUnit = normalizeUnit(product.getServingQuantityUnit());
        micronutrients.setServingQuantityValue(servingQuantity);
        micronutrients.setServingQuantityUnit(servingUnit);

        if (micronutrientValuesPerBasis != null && !micronutrientValuesPerBasis.isEmpty()) {
            micronutrients.setBiotin(findLocalPer100Micrograms(micronutrientValuesPerBasis, "biotin"));
            micronutrients.setCalcium(findLocalPer100Micrograms(micronutrientValuesPerBasis, "calcium"));
            micronutrients.setChloride(findLocalPer100Micrograms(micronutrientValuesPerBasis, "chloride"));
            micronutrients.setCholine(findLocalPer100Micrograms(micronutrientValuesPerBasis, "choline"));
            micronutrients.setChromium(findLocalPer100Micrograms(micronutrientValuesPerBasis, "chromium"));
            micronutrients.setCopper(findLocalPer100Micrograms(micronutrientValuesPerBasis, "copper"));
            micronutrients.setFluoride(findLocalPer100Micrograms(micronutrientValuesPerBasis, "fluoride"));
            micronutrients.setFolate(findLocalPer100Micrograms(micronutrientValuesPerBasis, "vitamin-b9"));
            micronutrients.setIodine(findLocalPer100Micrograms(micronutrientValuesPerBasis, "iodine"));
            micronutrients.setIron(findLocalPer100Micrograms(micronutrientValuesPerBasis, "iron"));
            micronutrients.setMagnesium(findLocalPer100Micrograms(micronutrientValuesPerBasis, "magnesium"));
            micronutrients.setManganese(findLocalPer100Micrograms(micronutrientValuesPerBasis, "manganese"));
            micronutrients.setMolybdenum(findLocalPer100Micrograms(micronutrientValuesPerBasis, "molybdenum"));
            micronutrients.setNiacin(findLocalPer100Micrograms(micronutrientValuesPerBasis, "vitamin-pp"));
            micronutrients.setPantothenicAcid(findLocalPer100Micrograms(micronutrientValuesPerBasis, "pantothenic-acid"));
            micronutrients.setPhosphorus(findLocalPer100Micrograms(micronutrientValuesPerBasis, "phosphorus"));
            micronutrients.setPotassium(findLocalPer100Micrograms(micronutrientValuesPerBasis, "potassium"));
            micronutrients.setRiboflavin(findLocalPer100Micrograms(micronutrientValuesPerBasis, "vitamin-b2"));
            micronutrients.setSelenium(findLocalPer100Micrograms(micronutrientValuesPerBasis, "selenium"));
            micronutrients.setSodium(findLocalPer100Micrograms(micronutrientValuesPerBasis, "sodium"));
            micronutrients.setThiamin(findLocalPer100Micrograms(micronutrientValuesPerBasis, "vitamin-b1"));
            micronutrients.setVitaminA(findLocalPer100Micrograms(micronutrientValuesPerBasis, "vitamin-a"));
            micronutrients.setVitaminB12(findLocalPer100Micrograms(micronutrientValuesPerBasis, "vitamin-b12"));
            micronutrients.setVitaminB6(findLocalPer100Micrograms(micronutrientValuesPerBasis, "vitamin-b6"));
            micronutrients.setVitaminC(findLocalPer100Micrograms(micronutrientValuesPerBasis, "vitamin-c"));
            micronutrients.setVitaminD(findLocalPer100Micrograms(micronutrientValuesPerBasis, "vitamin-d"));
            micronutrients.setVitaminE(findLocalPer100Micrograms(micronutrientValuesPerBasis, "vitamin-e"));
            micronutrients.setVitaminK(findLocalPer100Micrograms(micronutrientValuesPerBasis, "vitamin-k"));
            micronutrients.setZinc(findLocalPer100Micrograms(micronutrientValuesPerBasis, "zinc"));
        }

        pantryItem.setMicronutrients(micronutrients);
        pantryItemMicronutrientsRepository.save(micronutrients);
    }

    /**
     * @deprecated Local dataset nutrients are now stored per nutrition basis, not per package.
     * Kept as a compatibility shim for older call sites.
     */
    @Deprecated
    public void upsertMicronutrientsPerPackageFromLocalDataset(
            PantryItem pantryItem,
            LocalDatasetProductDTO product
    ) {
        upsertMicronutrientsPerBasisFromLocalDataset(pantryItem, product);
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

    private BigDecimal calculateLocalPackageAmount(
            Map<String, LocalDatasetProductDTO.NutrientAmountDTO> micronutrientsPer100,
            BigDecimal packageBasisAmount,
            String nutrientKey
    ) {
        BigDecimal per100Micrograms = findLocalPer100Micrograms(micronutrientsPer100, nutrientKey);
        if (per100Micrograms == null) {
            return null;
        }

        return per100Micrograms
                .multiply(packageBasisAmount)
                .divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal findLocalPer100Micrograms(
            Map<String, LocalDatasetProductDTO.NutrientAmountDTO> micronutrientsPer100,
            String nutrientKey
    ) {
        LocalDatasetProductDTO.NutrientAmountDTO amount = micronutrientsPer100.get(nutrientKey);
        if (amount == null || amount.getValue() == null) {
            return null;
        }

        BigDecimal value = parseBigDecimalOrNull(amount.getValue());
        if (value == null) {
            return null;
        }

        return convertToMicrograms(value, amount.getUnit());
    }

    private String normalizeUnit(String unit) {
        String cleaned = parseStringOrNull(unit);
        if (cleaned == null) {
            return null;
        }

        String normalized = cleaned.toLowerCase(Locale.ROOT);
        if (normalized.equals("g") || normalized.equals("ml")) {
            return normalized;
        }

        return null;
    }

    private String formatPackageQuantity(Double packageQuantity, String packageQuantityUnit) {
        if (packageQuantity == null) {
            return null;
        }

        String unit = parseStringOrNull(packageQuantityUnit);
        if (unit == null) {
            return String.valueOf(packageQuantity);
        }

        return packageQuantity + " " + unit;
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
