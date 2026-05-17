from typing import Any

from local_dataset_builder.config import NUTRIENT_KEYS_TO_KEEP
from local_dataset_builder.text_utils import clean_scalar, ensure_mapping
from local_dataset_builder.nutrition_utils import (
    standardize_all_nutrients,
    get_package_factor,
)

def first_not_none(*values: Any) -> Any:
    for value in values:
        if value is not None:
            return value

    return None


def extract_compact_from_nutrition(product: dict[str, Any]) -> dict[str, Any]:
    nutrition = ensure_mapping(product)

    if not nutrition:
        return {}

    aggregated_set = nutrition.get("aggregated_set")

    if not isinstance(aggregated_set, dict):
        return {}

    raw_nutrients = aggregated_set.get("nutrients")

    if not isinstance(raw_nutrients, dict):
        return {}

    compact_nutrients: dict[str, dict[str, Any]] = {}

    for nutrient_key in NUTRIENT_KEYS_TO_KEEP:
        nutrient = raw_nutrients.get(nutrient_key)

        if not isinstance(nutrient, dict):
            continue

        value = first_not_none(
            nutrient.get("value_computed"),
            nutrient.get("value"),
        )

        if value is None:
            continue

        unit = clean_scalar(nutrient.get("unit"))

        if unit is None:
            continue

        package_factor = get_package_factor(product)

        compact_nutrients[nutrient_key] = standardize_all_nutrients(
            nutrient_key,
            value,
            unit,
            package_factor
        )

    if not compact_nutrients:
        return {}

    return compact_nutrients


def extract_energy_kcal_fields(compact_nutrition: dict[str, Any]) -> tuple[str, str, str]:
    nutrients = compact_nutrition.get("nutrients")

    if not isinstance(nutrients, dict):
        return "", "", ""

    energy_kcal = nutrients.get("energy-kcal")

    if not isinstance(energy_kcal, dict):
        return "", "", ""

    return (
        clean_scalar(energy_kcal.get("value")),
        clean_scalar(compact_nutrition.get("per")),
        clean_scalar(energy_kcal.get("unit")),
    )