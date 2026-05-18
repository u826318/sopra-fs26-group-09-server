from typing import Any

from local_dataset_builder.config import OUTPUT_COLUMNS
from local_dataset_builder.image_handler import extract_image_1, extract_image_2
from local_dataset_builder.name_extractor import extract_name_candidates
from local_dataset_builder.nutrient_index import encode_nutrients_by_index
from local_dataset_builder.nutrition_handler import extract_compact_from_nutrition
from local_dataset_builder.nutrition_utils import (
    get_normalized_package_quantity,
    get_normalized_serving_quantity,
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

    package_quantity, package_unit = get_normalized_package_quantity(product)
    serving_quantity, serving_unit = get_normalized_serving_quantity(product)

    row["package_quantity"] = csv_safe(package_quantity)
    row["package_quantity_unit"] = csv_safe(package_unit)
    row["serving_quantity"] = csv_safe(serving_quantity)
    row["serving_quantity_unit"] = csv_safe(serving_unit)

    nutrition_compact, nutrition_basis_unit = extract_compact_from_nutrition(product)
    nutriments_compact, nutriments_basis_unit = build_compact_nutriments(product)

    # Prefer the newer Open Food Facts nutrition field. Fall back to legacy
    # nutriments only when the newer field cannot produce standardized nutrients.
    standardized_nutrients = nutrition_compact or nutriments_compact
    basis_unit = nutrition_basis_unit if nutrition_compact else nutriments_basis_unit

    indexed_nutrition = encode_nutrients_by_index(standardized_nutrients)

    row["nutrition_basis_unit"] = csv_safe(basis_unit)
    row["nutrition"] = csv_safe(indexed_nutrition) if indexed_nutrition else ""

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
