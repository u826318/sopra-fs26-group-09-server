package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import java.util.Map;

public final class LocalDatasetNutrientIndex {

  private LocalDatasetNutrientIndex() {
  }

  public static final Map<String, String> INDEX_TO_NUTRIENT_KEY = Map.ofEntries(
      Map.entry("0", "energy-kcal"),
      Map.entry("1", "energy-kj"),
      Map.entry("2", "fat"),
      Map.entry("3", "saturated-fat"),
      Map.entry("4", "carbohydrates"),
      Map.entry("5", "sugars"),
      Map.entry("6", "fiber"),
      Map.entry("7", "proteins"),
      Map.entry("8", "salt"),
      Map.entry("9", "sodium"),

      Map.entry("10", "calcium"),
      Map.entry("11", "choline"),
      Map.entry("12", "copper"),
      Map.entry("13", "iodine"),
      Map.entry("14", "iron"),
      Map.entry("15", "magnesium"),
      Map.entry("16", "manganese"),
      Map.entry("17", "phosphorus"),
      Map.entry("18", "potassium"),
      Map.entry("19", "selenium"),
      Map.entry("20", "zinc"),

      Map.entry("21", "vitamin-a"),
      Map.entry("22", "vitamin-b1"),
      Map.entry("23", "vitamin-b2"),
      Map.entry("24", "vitamin-b6"),
      Map.entry("25", "vitamin-b12"),
      Map.entry("26", "vitamin-c"),
      Map.entry("27", "vitamin-d"),
      Map.entry("28", "vitamin-e"),
      Map.entry("29", "vitamin-k"),
      Map.entry("30", "vitamin-b9"),
      Map.entry("31", "vitamin-pp"),
      Map.entry("32", "pantothenic-acid"),

      Map.entry("33", "biotin"),
      Map.entry("34", "chloride"),
      Map.entry("35", "chromium"),
      Map.entry("36", "fluoride"),
      Map.entry("37", "molybdenum")
  );
}