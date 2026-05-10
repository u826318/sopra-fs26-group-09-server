from typing import Any

from local_dataset_builder.config import NUTRIENT_KEYS_TO_KEEP
from local_dataset_builder.text_utils import clean_scalar, ensure_mapping


def first_not_none(*values: Any) -> Any:
    for value in values:
        if value is not None:
            return value

    return None


def build_compact_nutriments(nutriments_value: Any) -> dict[str, Any]:
    nutriments = ensure_mapping(nutriments_value)

    if not nutriments:
        return {}

    compact_nutrients: dict[str, dict[str, Any]] = {}

    for nutrient_key in NUTRIENT_KEYS_TO_KEEP:
        value = first_not_none(
            nutriments.get(f"{nutrient_key}_100g"),
            nutriments.get(f"{nutrient_key}_100ml"),
            nutriments.get(f"{nutrient_key}_serving"),
            nutriments.get(nutrient_key),
        )

        if value is None:
            continue

        unit = first_not_none(
            nutriments.get(f"{nutrient_key}_unit"),
            nutriments.get(f"{nutrient_key}_value_unit"),
            "",
        )

        compact_nutrients[nutrient_key] = {
            "value": value,
            "unit": clean_scalar(unit),
        }

    if not compact_nutrients:
        return {}

    return {
        "per": "100g",
        "nutrients": compact_nutrients,
    }