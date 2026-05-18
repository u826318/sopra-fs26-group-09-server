from typing import Any


# Stable, append-only nutrition schema for the compact local dataset.
#
# The nutrition JSON cell is now a FLAT indexed dictionary:
#
#   {"0": 533.0, "1": 30.9, "13": 3300.0}
#
# The basis is NOT encoded inside the JSON anymore. It is stored in the CSV
# column `nutrition_basis_unit`:
#
#   nutrition_basis_unit = "g"  -> all values are per 100g
#   nutrition_basis_unit = "ml" -> all values are per 100ml
#
# Unit meaning after handler standardization:
# - energy-kcal: kcal per 100g/ml
# - core nutrition keys: grams per 100g/ml
# - micronutrient keys: micrograms per 100g/ml
#
# IMPORTANT: Do not reorder existing keys after a dataset has been generated.
# Append new keys to the end only, then mirror the same mapping in Java.
ENERGY_KCAL = [
    "energy-kcal",
]

CORE_NUTRITION_KEYS = [
    "fat",
    "saturated-fat",
    "carbohydrates",
    "sugars",
    "fiber",
    "proteins",
    "salt",
]

# Backward-compatible alias for older modules/imports.
CORE_NUTRIENT_KEYS = CORE_NUTRITION_KEYS

MICRONUTRIENT_KEYS = [
    "sodium",

    "calcium",
    "choline",
    "copper",
    "iodine",
    "iron",
    "magnesium",
    "manganese",
    "phosphorus",
    "potassium",
    "selenium",
    "zinc",

    "vitamin-a",
    "vitamin-b1",
    "vitamin-b2",
    "vitamin-b6",
    "vitamin-b12",
    "vitamin-c",
    "vitamin-d",
    "vitamin-e",
    "vitamin-k",
    "vitamin-b9",
    "vitamin-pp",
    "pantothenic-acid",

    "biotin",
    "chloride",
    "chromium",
    "fluoride",
    "molybdenum",
]

CORE_NUTRITION_SCHEMA_KEYS = ENERGY_KCAL + CORE_NUTRITION_KEYS
NUTRIENT_KEYS_TO_KEEP = CORE_NUTRITION_SCHEMA_KEYS + MICRONUTRIENT_KEYS

NUTRIENT_KEY_TO_INDEX = {
    nutrient_key: str(index)
    for index, nutrient_key in enumerate(NUTRIENT_KEYS_TO_KEEP)
}

NUTRIENT_INDEX_TO_KEY = {
    str(index): nutrient_key
    for index, nutrient_key in enumerate(NUTRIENT_KEYS_TO_KEEP)
}


def encode_nutrients_by_index(nutrients: dict[str, Any]) -> dict[str, float]:
    """Encode one name-keyed nutrient dict into a flat indexed dict."""
    encoded: dict[str, float] = {}

    for nutrient_key in NUTRIENT_KEYS_TO_KEEP:
        raw_value = nutrients.get(nutrient_key)

        if raw_value is None:
            continue

        try:
            value = float(raw_value)
        except (TypeError, ValueError):
            continue

        nutrient_index = NUTRIENT_KEY_TO_INDEX[nutrient_key]
        encoded[nutrient_index] = value

    return encoded
