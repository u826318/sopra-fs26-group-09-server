from typing import Any

from local_dataset_builder.config import OUTPUT_COLUMNS
from local_dataset_builder.image_handler import extract_image_1, extract_image_2
from local_dataset_builder.name_extractor import extract_name_candidates
from local_dataset_builder.nutrition_handler import (
    extract_compact_from_nutrition,
    extract_energy_kcal_fields,
)
from local_dataset_builder.nutriments_handler import build_compact_nutriments
from local_dataset_builder.text_utils import clean_scalar, csv_safe


def build_preview_row(product: dict[str, Any]) -> dict[str, str]:
    row = {column: "" for column in OUTPUT_COLUMNS}

    row["code"] = csv_safe(product.get("code") or product.get("_id"))
    row["brands"] = csv_safe(product.get("brands"))
    row["name_candidates"] = csv_safe(extract_name_candidates(product))

    row["product_quantity"] = csv_safe(product.get("product_quantity"))
    row["product_quantity_unit"] = csv_safe(product.get("product_quantity_unit"))
    row["serving_quantity"] = csv_safe(product.get("serving_quantity"))
    row["serving_quantity_unit"] = csv_safe(product.get("serving_quantity_unit"))

    nutrition_compact = extract_compact_from_nutrition(product.get("nutrition"))
    nutriments_compact = build_compact_nutriments(product.get("nutriments"))

    best_compact = nutrition_compact or nutriments_compact
    energy_value, energy_basis, energy_unit = extract_energy_kcal_fields(best_compact)

    row["energy_kcal_value"] = csv_safe(energy_value)
    row["energy_kcal_basis"] = csv_safe(energy_basis)
    row["energy_kcal_unit"] = csv_safe(energy_unit)

    row["nutrition_compact"] = csv_safe(nutrition_compact)
    row["nutriments_compact"] = csv_safe(nutriments_compact)

    row["image_1"] = csv_safe(extract_image_1(product.get("images")))
    row["image_2"] = csv_safe(extract_image_2(product.get("images")))

    return row


def has_minimum_product_data(product: dict[str, Any]) -> bool:
    code = clean_scalar(product.get("code") or product.get("_id"))

    if not code:
        return False

    name_candidates = extract_name_candidates(product)
    brands = clean_scalar(product.get("brands"))

    return bool(name_candidates or brands)