import re
from typing import Any

from local_dataset_builder.text_utils import clean_scalar
from local_dataset_builder.config import (
    ENERGY_KCAL, 
    CORE_NUTRIENT_KEYS, 
    MICRONUTRIENT_KEYS
)

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

def parse_float(value: Any) -> float | None:
    text = clean_scalar(value)

    if not text:
        return None

    text = text.replace(",", ".")
    match = re.search(r"\d+(?:\.\d+)?", text)

    if not match:
        return None

    try:
        return float(match.group(0))
    except ValueError:
        return None
    
def get_package_factor(product: dict[str, Any]) -> float | None:
    warnings: list[str] = []

    package_quantity = standardize_to_g_or_ml(
        product.get("product_quantity"),
    )

    if package_quantity is None:
        return None

    return package_quantity / 100.0

def convert_mass_to_g(value: Any, unit: Any) -> float | None:

    if value is None:
        return None

    factor = MASS_UNITS_TO_G_FACTOR.get(unit)

    if factor is None:
        return None

    return value * factor

def convert_volume_to_ml(value: Any, unit: Any) -> float | None:

    if value is None:
        return None

    factor = MASS_UNITS_TO_G_FACTOR.get(unit)

    if factor is None:
        return None

    return value * factor

def standardize_to_g_or_ml(value: Any, unit: Any) -> float | None:

    value = parse_float(value)
    unit = clean_scalar(unit).lower()

    ml_quantity = convert_volume_to_ml(value, unit)
    g_quantity = convert_mass_to_g(value, unit)

    if ml_quantity is not None:
        return ml_quantity
    
    if g_quantity is not None:
        return g_quantity
    
    return None

def convert_mass_to_micrograms(value: Any, unit: Any) -> float | None:
    numeric_value = parse_float(value)
    cleaned_unit = clean_scalar(unit).lower()

    if numeric_value is None:
        return None

    factor = MASS_UNITS_TO_UG_FACTOR.get(cleaned_unit)

    if factor is None:
        return None

    return numeric_value * factor

def standardize_micronutrient(
    value: Any,
    unit: Any,
    package_factor: float,
) -> float | None:
    micrograms_per_100 = convert_mass_to_micrograms(value, unit)
    
    if micrograms_per_100 is None:
        return None
    if package_factor is None:
        return None

    return micrograms_per_100 * package_factor

def standardize_core_nutrient(
    value: Any,
    unit: Any,
    package_factor: float,
) -> float | None:
    g_per_100 = standardize_to_g_or_ml(value, unit)

    if g_per_100 is None or package_factor is None:
        return None
    return g_per_100 * package_factor

def standardize_energy(
    value: Any,
    package_factor: float,
) -> float | None:

    if package_factor is None or value is None:
        return None

    return package_factor * value

def standardize_all_nutrients(
    nutrient_key: str,
    value: Any,
    unit: Any,
    package_factor: float,
) -> float | None:
    if nutrient_key in ENERGY_KCAL:
        return standardize_energy(value, package_factor)
    if nutrient_key in CORE_NUTRIENT_KEYS:
        return standardize_core_nutrient(value, unit, package_factor)
    if nutrient_key in MICRONUTRIENT_KEYS:
        return standardize_micronutrient(value, unit, package_factor)
    return None