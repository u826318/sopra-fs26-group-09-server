from typing import Any

from local_dataset_builder.config import NUTRIENT_KEYS_TO_KEEP
from local_dataset_builder.nutrition_utils import (
    SOURCE_BASIS_ASSUMED_100,
    add_standardized_per100_nutrient,
    choose_best_per100_group,
    first_not_none,
    normalize_source_basis,
)
from local_dataset_builder.text_utils import clean_scalar, ensure_mapping


def extract_compact_from_nutrition(product: dict[str, Any]) -> tuple[dict[str, float], str | None]:
    """Extract standardized per-100 nutrients from newer OFF `nutrition`.

    Output is a flat name-keyed nutrient dictionary plus one basis unit:
    - basis_unit = "g"  -> nutrient values are per 100g
    - basis_unit = "ml" -> nutrient values are per 100ml

    If the source is per serving and serving quantity is available, values are
    converted to per 100g/ml. If serving quantity is unavailable, those nutrients
    are skipped.
    """
    nutrition = ensure_mapping(product.get("nutrition"))

    if not nutrition:
        return {}, None

    aggregated_set = nutrition.get("aggregated_set")

    if not isinstance(aggregated_set, dict):
        return {}, None

    raw_nutrients = aggregated_set.get("nutrients")

    if not isinstance(raw_nutrients, dict):
        return {}, None

    source_basis = normalize_source_basis(
        first_not_none(
            aggregated_set.get("per"),
            product.get("nutrition_data_per"),
        )
    ) or SOURCE_BASIS_ASSUMED_100

    grouped_by_basis_unit: dict[str, dict[str, float]] = {}

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

        if not unit:
            continue

        add_standardized_per100_nutrient(
            grouped_by_basis_unit,
            product,
            source_basis,
            nutrient_key,
            value,
            unit,
        )

    return choose_best_per100_group(grouped_by_basis_unit)
