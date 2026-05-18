import re
from typing import Any

from local_dataset_builder.config import (
    CORE_NUTRITION_KEYS,
    ENERGY_KCAL,
    MICRONUTRIENT_KEYS,
)
from local_dataset_builder.text_utils import clean_scalar


MASS_UNITS_TO_G = {
    "g": 1.0,
    "gram": 1.0,
    "grams": 1.0,
    "kg": 1000.0,
    "kilogram": 1000.0,
    "kilograms": 1000.0,
}

VOLUME_UNITS_TO_ML = {
    "ml": 1.0,
    "milliliter": 1.0,
    "milliliters": 1.0,
    "millilitre": 1.0,
    "millilitres": 1.0,
    "cl": 10.0,
    "dl": 100.0,
    "l": 1000.0,
    "liter": 1000.0,
    "liters": 1000.0,
    "litre": 1000.0,
    "litres": 1000.0,
}

MASS_UNITS_TO_G_FACTOR = {
    "g": 1.0,
    "gram": 1.0,
    "grams": 1.0,
    "mg": 0.001,
    "milligram": 0.001,
    "milligrams": 0.001,
    "µg": 0.000001,
    "μg": 0.000001,
    "ug": 0.000001,
    "mcg": 0.000001,
    "microgram": 0.000001,
    "micrograms": 0.000001,
}

MASS_UNITS_TO_UG_FACTOR = {
    "g": 1_000_000.0,
    "gram": 1_000_000.0,
    "grams": 1_000_000.0,
    "mg": 1_000.0,
    "milligram": 1_000.0,
    "milligrams": 1_000.0,
    "µg": 1.0,
    "μg": 1.0,
    "ug": 1.0,
    "mcg": 1.0,
    "microgram": 1.0,
    "micrograms": 1.0,
}

ENERGY_KCAL_UNITS = {
    "kcal",
    "calorie",
    "calories",
}

# Internal source-basis labels. These are NOT written into the nutrition JSON.
# The generated dataset uses a flat nutrition dictionary and a separate
# `nutrition_basis_unit` column with value "g" or "ml".
SOURCE_BASIS_100G = "100g"
SOURCE_BASIS_100ML = "100ml"
SOURCE_BASIS_SERVING = "serving"
SOURCE_BASIS_ASSUMED_100 = "assumed100"

PER_100_G = "g"
PER_100_ML = "ml"


def first_not_none(*values: Any) -> Any:
    for value in values:
        if value is not None:
            return value
    return None


def parse_float(value: Any) -> float | None:
    if value is None:
        return None

    if isinstance(value, bool):
        return None

    if isinstance(value, (int, float)):
        return float(value)

    text = clean_scalar(value)

    if not text:
        return None

    text = text.replace(",", ".")

    # First try the whole string. This correctly handles:
    # "7.0774e-06", "8.814e-08", "0.144", "467"
    try:
        return float(text)
    except ValueError:
        pass

    # Fallback for strings like "11.5 g" or "6 x 90 g".
    match = re.search(
        r"-?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?",
        text,
    )

    if not match:
        return None

    try:
        return float(match.group(0))
    except ValueError:
        return None


def convert_package_mass_to_g(value: Any, unit: Any) -> float | None:
    numeric_value = parse_float(value)
    cleaned_unit = clean_scalar(unit).lower()

    if numeric_value is None:
        return None

    factor = MASS_UNITS_TO_G.get(cleaned_unit)

    if factor is None:
        return None

    return numeric_value * factor


def convert_package_volume_to_ml(value: Any, unit: Any) -> float | None:
    numeric_value = parse_float(value)
    cleaned_unit = clean_scalar(unit).lower()

    if numeric_value is None:
        return None

    factor = VOLUME_UNITS_TO_ML.get(cleaned_unit)

    if factor is None:
        return None

    return numeric_value * factor


def standardize_package_quantity_to_g_or_ml(value: Any, unit: Any) -> tuple[float | None, str | None]:
    ml_quantity = convert_package_volume_to_ml(value, unit)

    if ml_quantity is not None:
        return ml_quantity, PER_100_ML

    g_quantity = convert_package_mass_to_g(value, unit)

    if g_quantity is not None:
        return g_quantity, PER_100_G

    return None, None


# Backward-compatible alias for older code/imports.
def standardize_to_g_or_ml(value: Any, unit: Any) -> tuple[float | None, str | None]:
    return standardize_package_quantity_to_g_or_ml(value, unit)


def parse_quantity_text_to_g_or_ml(text_value: Any) -> tuple[float | None, str | None]:
    """Parse clear package/serving quantity text into normalized g/ml.

    Handles simple forms such as "400 g", "1 L", and multipack forms such as
    "6 x 90 g". When a text contains both an imperial and metric quantity, e.g.
    "1.61 oz (45 g)", the metric pair is preferred because those are the only
    units we standardize here.
    """
    text = clean_scalar(text_value).lower().replace(",", ".")

    if not text:
        return None, None

    units = sorted(
        list(MASS_UNITS_TO_G.keys()) + list(VOLUME_UNITS_TO_ML.keys()),
        key=len,
        reverse=True,
    )

    for unit in units:
        multipack_pattern = rf"(-?\d+(?:\.\d+)?)\s*(?:x|×|\*)\s*(-?\d+(?:\.\d+)?)\s*{re.escape(unit)}\b"
        multipack_matches = list(re.finditer(multipack_pattern, text))

        if multipack_matches:
            match = multipack_matches[-1]
            count = parse_float(match.group(1))
            amount = parse_float(match.group(2))

            if count is None or amount is None:
                continue

            return standardize_package_quantity_to_g_or_ml(count * amount, unit)

    for unit in units:
        simple_pattern = rf"(-?\d+(?:\.\d+)?)\s*{re.escape(unit)}\b"
        simple_matches = list(re.finditer(simple_pattern, text))

        if simple_matches:
            # Use the last metric-looking pair. This handles strings such as
            # "1.61 oz (45 g)" by selecting "45 g".
            amount = parse_float(simple_matches[-1].group(1))

            if amount is None:
                continue

            return standardize_package_quantity_to_g_or_ml(amount, unit)

    return None, None


def get_normalized_package_quantity(product: dict[str, Any]) -> tuple[float | None, str | None]:
    package_quantity, package_unit = standardize_package_quantity_to_g_or_ml(
        product.get("product_quantity"),
        product.get("product_quantity_unit"),
    )

    if package_quantity is not None and package_unit is not None:
        return package_quantity, package_unit

    return parse_quantity_text_to_g_or_ml(product.get("quantity"))


def get_normalized_serving_quantity(product: dict[str, Any]) -> tuple[float | None, str | None]:
    quantity = first_not_none(
        product.get("serving_quantity"),
        product.get("serving_size"),
    )
    unit = first_not_none(
        product.get("serving_quantity_unit"),
        # serving_size may contain its own unit, e.g. "45 g".
        "",
    )

    if product.get("serving_quantity_unit"):
        return standardize_package_quantity_to_g_or_ml(quantity, unit)

    # If serving_size is a string like "1 bar (45 g)" or "45 g", infer the
    # metric amount from the text itself.
    return parse_quantity_text_to_g_or_ml(product.get("serving_size"))


def normalize_source_basis(raw_basis: Any) -> str | None:
    text = clean_scalar(raw_basis).lower().replace(" ", "")

    if not text:
        return None

    if text in {"100g", "per100g", "100grams", "g"}:
        return SOURCE_BASIS_100G

    if text in {"100ml", "per100ml", "ml"}:
        return SOURCE_BASIS_100ML

    if text in {"serving", "perserving", "servings", "portion", "perportion"}:
        return SOURCE_BASIS_SERVING

    return None


def basis_from_product_nutrition_data_per(product: dict[str, Any]) -> str | None:
    return normalize_source_basis(product.get("nutrition_data_per"))


def infer_assumed_per100_unit(product: dict[str, Any]) -> str:
    """Infer whether assumed per-100 values should be treated as g or ml.

    This is used only when OFF gives *_value / *_unit without a clear basis.
    We prefer actual package quantity, then serving quantity, then raw quantity
    text. If nothing is usable, default to grams to preserve the row.
    """
    _, package_unit = get_normalized_package_quantity(product)

    if package_unit in {PER_100_G, PER_100_ML}:
        return package_unit

    _, serving_unit = get_normalized_serving_quantity(product)

    if serving_unit in {PER_100_G, PER_100_ML}:
        return serving_unit

    _, quantity_text_unit = parse_quantity_text_to_g_or_ml(product.get("quantity"))

    if quantity_text_unit in {PER_100_G, PER_100_ML}:
        return quantity_text_unit

    return PER_100_G


def convert_mass_to_g(value: Any, unit: Any) -> float | None:
    numeric_value = parse_float(value)
    cleaned_unit = clean_scalar(unit).lower()

    if numeric_value is None:
        return None

    factor = MASS_UNITS_TO_G_FACTOR.get(cleaned_unit)

    if factor is None:
        return None

    return numeric_value * factor


def convert_mass_to_micrograms(value: Any, unit: Any) -> float | None:
    numeric_value = parse_float(value)
    cleaned_unit = clean_scalar(unit).lower()

    if numeric_value is None:
        return None

    factor = MASS_UNITS_TO_UG_FACTOR.get(cleaned_unit)

    if factor is None:
        return None

    return numeric_value * factor


def standardize_energy(value: Any, unit: Any) -> float | None:
    numeric_value = parse_float(value)
    cleaned_unit = clean_scalar(unit).lower()

    if numeric_value is None or cleaned_unit not in ENERGY_KCAL_UNITS:
        return None

    return numeric_value


def standardize_core_nutrient(value: Any, unit: Any) -> float | None:
    return convert_mass_to_g(value, unit)


def standardize_micronutrient(value: Any, unit: Any) -> float | None:
    return convert_mass_to_micrograms(value, unit)


def standardize_all_nutrients(
    nutrient_key: str,
    value: Any,
    unit: Any,
) -> float | None:
    """Standardize one nutrient value before basis conversion.

    Returned value meaning before source-basis conversion:
    - energy-kcal: kcal per source basis
    - core nutrition keys: grams per source basis
    - micronutrients: micrograms per source basis
    """
    if nutrient_key in ENERGY_KCAL:
        return standardize_energy(value, unit)

    if nutrient_key in CORE_NUTRITION_KEYS:
        return standardize_core_nutrient(value, unit)

    if nutrient_key in MICRONUTRIENT_KEYS:
        return standardize_micronutrient(value, unit)

    return None


def convert_standardized_value_to_per100(
    standardized_value: float,
    source_basis: str | None,
    product: dict[str, Any],
) -> tuple[float | None, str | None]:
    """Convert a standardized value into per-100g or per-100ml form.

    `nutrition_basis_unit` in the output CSV records whether the returned value
    is per 100g (`g`) or per 100ml (`ml`).
    """
    if source_basis == SOURCE_BASIS_100G:
        return standardized_value, PER_100_G

    if source_basis == SOURCE_BASIS_100ML:
        return standardized_value, PER_100_ML

    if source_basis == SOURCE_BASIS_SERVING:
        serving_quantity, serving_unit = get_normalized_serving_quantity(product)

        if serving_quantity is None or serving_quantity <= 0 or serving_unit not in {PER_100_G, PER_100_ML}:
            return None, None

        return standardized_value * 100.0 / serving_quantity, serving_unit

    # OFF *_value / *_unit without a clean basis: preserve it as an assumed
    # per-100 value and infer whether that should be g or ml.
    if source_basis == SOURCE_BASIS_ASSUMED_100 or source_basis is None:
        return standardized_value, infer_assumed_per100_unit(product)

    return None, None


def standardize_nutrient_to_per100(
    product: dict[str, Any],
    source_basis: str | None,
    nutrient_key: str,
    value: Any,
    unit: Any,
) -> tuple[float | None, str | None]:
    standardized_value = standardize_all_nutrients(nutrient_key, value, unit)

    if standardized_value is None:
        return None, None

    return convert_standardized_value_to_per100(
        standardized_value,
        source_basis,
        product,
    )


def add_standardized_per100_nutrient(
    grouped_by_basis_unit: dict[str, dict[str, float]],
    product: dict[str, Any],
    source_basis: str | None,
    nutrient_key: str,
    value: Any,
    unit: Any,
) -> bool:
    per100_value, basis_unit = standardize_nutrient_to_per100(
        product,
        source_basis,
        nutrient_key,
        value,
        unit,
    )

    if per100_value is None or basis_unit not in {PER_100_G, PER_100_ML}:
        return False

    grouped_by_basis_unit.setdefault(basis_unit, {})[nutrient_key] = per100_value
    return True


def choose_best_per100_group(
    grouped_by_basis_unit: dict[str, dict[str, float]],
) -> tuple[dict[str, float], str | None]:
    """Choose one flat per-100 nutrition group.

    The CSV nutrition cell is intentionally flat, so we cannot store both per100g
    and per100ml values in the same row. If both exist, keep the group with more
    nutrients. Ties prefer grams because food products are more often mass-based.
    """
    candidates = [
        (PER_100_G, grouped_by_basis_unit.get(PER_100_G, {})),
        (PER_100_ML, grouped_by_basis_unit.get(PER_100_ML, {})),
    ]
    candidates = [(basis_unit, nutrients) for basis_unit, nutrients in candidates if nutrients]

    if not candidates:
        return {}, None

    candidates.sort(
        key=candidate_sort_score,
        reverse=True,
    )

    return candidates[0][1], candidates[0][0]

def candidate_sort_score(item):
    basis_unit = item[0]
    nutrients = item[1]

    nutrient_count = len(nutrients)

    if basis_unit == PER_100_G:
        gram_preference_score = 1
    else:
        gram_preference_score = 0

    return nutrient_count, gram_preference_score


