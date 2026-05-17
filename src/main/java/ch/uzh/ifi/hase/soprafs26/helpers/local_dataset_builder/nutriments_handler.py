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


def build_compact_nutriments(product: dict[str, Any]) -> dict[str, Any]:
    nutriments = ensure_mapping(product.get(nutriments))
    package_factor = get_package_factor(product)

    if not nutriments:
        return {}

    compact_nutrients: dict[str, dict[str, Any]] = {}

    for nutrient_key in NUTRIENT_KEYS_TO_KEEP:
        value = nutriments.get(f"{nutrient_key}_value"),

        if value is None:
            continue

        unit = nutriments.get(f"{nutrient_key}_unit")

        if unit is None:
            continue

        compact_nutrients[nutrient_key] = standardize_all_nutrients(
            nutrient_key, 
            value, 
            unit, 
            package_factor
        )

    if not compact_nutrients:
        return {}

    return compact_nutrients