package ch.uzh.ifi.hase.soprafs26.service.reference;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import ch.uzh.ifi.hase.soprafs26.entity.LifeStageGroup;
import jakarta.annotation.PostConstruct;

/**
 * Loads the micronutrient reference CSV once when the Spring Boot backend starts.
 *
 * The loaded rows are kept as ordinary Java objects in backend memory. Requests
 * can then query this already-loaded data instead of reopening and reparsing the
 * CSV file every time.
 */
@Component
public class MicronutrientReferenceCsvLoader {

    private static final String CSV_RESOURCE_PATH = "dri/micronutrient_references.csv";

    private final List<MicronutrientReferenceRow> rows = new ArrayList<>();
    private final Map<LifeStageGroup, List<MicronutrientReferenceRow>> rowsByLifeStageGroup =
        new EnumMap<>(LifeStageGroup.class);

    @PostConstruct
    public void loadCsv() {
        ClassPathResource resource = new ClassPathResource(CSV_RESOURCE_PATH);

        try (
            InputStream inputStream = resource.getInputStream();
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8)
            )
        ) {
            loadRows(reader);
        } catch (Exception exception) {
            throw new IllegalStateException(
                "Failed to load micronutrient reference CSV from classpath resource: " + CSV_RESOURCE_PATH,
                exception
            );
        }
    }

    public List<MicronutrientReferenceRow> getRows() {
        return Collections.unmodifiableList(rows);
    }

    public List<MicronutrientReferenceRow> getRowsForLifeStageGroup(LifeStageGroup lifeStageGroup) {
        return Collections.unmodifiableList(
            rowsByLifeStageGroup.getOrDefault(lifeStageGroup, List.of())
        );
    }

    private void loadRows(BufferedReader reader) throws Exception {
        rows.clear();
        rowsByLifeStageGroup.clear();

        String headerLine = reader.readLine();
        if (headerLine == null || headerLine.isBlank()) {
            throw new IllegalStateException("Micronutrient reference CSV is empty.");
        }

        List<String> headers = parseCsvLine(stripUtf8Bom(headerLine));
        Map<String, Integer> columnIndexes = buildColumnIndexes(headers);

        String line;
        int lineNumber = 1;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.isBlank()) {
                continue;
            }

            List<String> values = parseCsvLine(line);
            MicronutrientReferenceRow row = toRow(values, columnIndexes, lineNumber);
            rows.add(row);
            rowsByLifeStageGroup
                .computeIfAbsent(row.getLifeStageGroup(), ignored -> new ArrayList<>())
                .add(row);
        }
    }

    private Map<String, Integer> buildColumnIndexes(List<String> headers) {
        Map<String, Integer> indexes = new HashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            indexes.put(headers.get(index), index);
        }

        requireColumn(indexes, "standard");
        requireColumn(indexes, "nutrient_key");
        requireColumn(indexes, "display_name");
        requireColumn(indexes, "category");
        requireColumn(indexes, "life_stage_group");
        requireColumn(indexes, "age_min_months");
        requireColumn(indexes, "age_max_months");
        requireColumn(indexes, "unit");
        requireColumn(indexes, "rda_value");
        requireColumn(indexes, "ai_value");
        requireColumn(indexes, "ul_value");
        requireColumn(indexes, "source_files");

        return indexes;
    }

    private void requireColumn(Map<String, Integer> columnIndexes, String columnName) {
        if (!columnIndexes.containsKey(columnName)) {
            throw new IllegalStateException("Micronutrient reference CSV is missing required column: " + columnName);
        }
    }

    private MicronutrientReferenceRow toRow(
        List<String> values,
        Map<String, Integer> columnIndexes,
        int lineNumber
    ) {
        MicronutrientReferenceRow row = new MicronutrientReferenceRow();

        row.setStandard(getValue(values, columnIndexes, "standard"));
        row.setNutrientKey(getValue(values, columnIndexes, "nutrient_key"));
        row.setDisplayName(getValue(values, columnIndexes, "display_name"));
        row.setCategory(getValue(values, columnIndexes, "category"));
        row.setLifeStageGroup(parseLifeStageGroup(getValue(values, columnIndexes, "life_stage_group"), lineNumber));
        row.setAgeMinMonths(parseInteger(getValue(values, columnIndexes, "age_min_months"), "age_min_months", lineNumber));
        row.setAgeMaxMonths(parseInteger(getValue(values, columnIndexes, "age_max_months"), "age_max_months", lineNumber));
        row.setUnit(getValue(values, columnIndexes, "unit"));
        row.setRdaValue(parseBigDecimal(getValue(values, columnIndexes, "rda_value"), "rda_value", lineNumber));
        row.setAiValue(parseBigDecimal(getValue(values, columnIndexes, "ai_value"), "ai_value", lineNumber));
        row.setUlValue(parseBigDecimal(getValue(values, columnIndexes, "ul_value"), "ul_value", lineNumber));
        row.setSourceFiles(getValue(values, columnIndexes, "source_files"));

        return row;
    }

    private String getValue(List<String> values, Map<String, Integer> columnIndexes, String columnName) {
        int index = columnIndexes.get(columnName);
        if (index >= values.size()) {
            return "";
        }
        return values.get(index).trim();
    }

    private LifeStageGroup parseLifeStageGroup(String value, int lineNumber) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing life_stage_group on CSV line " + lineNumber);
        }

        String normalizedValue = value.trim();
        if ("LACTATING".equals(normalizedValue)) {
            normalizedValue = LifeStageGroup.BREASTFEEDING.name();
        }

        try {
            return LifeStageGroup.valueOf(normalizedValue);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                "Unknown life_stage_group '" + value + "' on CSV line " + lineNumber,
                exception
            );
        }
    }

    private Integer parseInteger(String value, String columnName, int lineNumber) {
        if (value == null || value.isBlank() || value.equals("ND")) {
            return null;
        }

        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                "Invalid integer value '" + value + "' for column " + columnName + " on CSV line " + lineNumber,
                exception
            );
        }
    }

    private BigDecimal parseBigDecimal(String value, String columnName, int lineNumber) {
        if (value == null || value.isBlank() || value.equals("ND")) {
            return null;
        }

        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                "Invalid decimal value '" + value + "' for column " + columnName + " on CSV line " + lineNumber,
                exception
            );
        }
    }

    private String stripUtf8Bom(String value) {
        if (value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }

    private List<String> parseCsvLine(String line) {
        return new ArrayList<>(Arrays.asList(line.split(",", -1)));
    }
}
