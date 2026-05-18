from typing import Any

from local_dataset_builder.config import NUTRIENT_KEYS_TO_KEEP
from local_dataset_builder.nutrition_utils import (
    SOURCE_BASIS_100G,
    SOURCE_BASIS_100ML,
    SOURCE_BASIS_ASSUMED_100,
    SOURCE_BASIS_SERVING,
    add_standardized_per100_nutrient,
    basis_from_product_nutrition_data_per,
    choose_best_per100_group,
    first_not_none,
)
from local_dataset_builder.text_utils import ensure_mapping


def build_compact_nutriments(product: dict[str, Any]) -> tuple[dict[str, float], str | None]:
    """Extract standardized per-100 nutrients from legacy OFF `nutriments`.

    Values are standardized into one flat dictionary whose values mean either
    per 100g or per 100ml, depending on the returned basis unit.
    """
    nutriments = ensure_mapping(product.get("nutriments"))

    if not nutriments:
        return {}, None

    grouped_by_basis_unit: dict[str, dict[str, float]] = {}
    value_basis = basis_from_product_nutrition_data_per(product) or SOURCE_BASIS_ASSUMED_100

    for nutrient_key in NUTRIENT_KEYS_TO_KEEP:
        unit = first_not_none(
            nutriments.get(f"{nutrient_key}_unit"),
            nutriments.get(f"{nutrient_key}_value_unit"),
        )

        if unit is None:
            continue

        # Prefer explicitly basis-tagged values. Per-serving values are converted
        # to per 100g/ml when serving quantity is available. Fall back to
        # *_value / bare key using nutrition_data_per if present; otherwise treat
        # it as assumed per 100 and infer g/ml from package/serving metadata.
        candidates = [
            (nutriments.get(f"{nutrient_key}_100g"), SOURCE_BASIS_100G),
            (nutriments.get(f"{nutrient_key}_100ml"), SOURCE_BASIS_100ML),
            (nutriments.get(f"{nutrient_key}_serving"), SOURCE_BASIS_SERVING),
            (nutriments.get(f"{nutrient_key}_value"), value_basis),
            (nutriments.get(nutrient_key), value_basis),
        ]

        for value, source_basis in candidates:
            if value is None:
                continue

            added = add_standardized_per100_nutrient(
                grouped_by_basis_unit,
                product,
                source_basis,
                nutrient_key,
                value,
                unit,
            )

            if added:
                break

    return choose_best_per100_group(grouped_by_basis_unit)
