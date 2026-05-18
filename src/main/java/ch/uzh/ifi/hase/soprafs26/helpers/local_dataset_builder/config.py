import os
from pathlib import Path

from local_dataset_builder.nutrient_index import (
    CORE_NUTRIENT_KEYS,
    CORE_NUTRITION_KEYS,
    ENERGY_KCAL,
    MICRONUTRIENT_KEYS,
    NUTRIENT_KEYS_TO_KEEP,
)


# This file lives inside:
#
#   <dataset_dir>/local_dataset_builder/config.py
#
# By default, the raw Open Food Facts dump and generated outputs live in
# the parent directory of this helper package:
#
#   <dataset_dir>/openfoodfacts-products.jsonl.gz
#   <dataset_dir>/local_dataset_builder_outputs/
#
# You can override the directory without editing code:
#
#   LOCAL_DATASET_DIR=/path/to/dataset-dir python -m local_dataset_builder.main
#
PACKAGE_DIR = Path(__file__).resolve().parent
DATASET_DIR = Path(os.environ.get("LOCAL_DATASET_DIR", PACKAGE_DIR.parent)).resolve()

INPUT_FILE = Path(
    os.environ.get("OFF_INPUT_FILE", DATASET_DIR / "openfoodfacts-products.jsonl.gz")
).resolve()

OUTPUT_DIR = Path(
    os.environ.get("LOCAL_DATASET_OUTPUT_DIR", DATASET_DIR / "local_dataset_builder_outputs")
).resolve()

OUTPUT_FILE = OUTPUT_DIR / "local_dataset.csv"
INDEXED_OUTPUT_FILE = OUTPUT_DIR / "local_dataset_indexed.csv"
PRODUCT_INDEX_COLUMN = "product_index"

LOCAL_DATASET_OUTPUT_DIR = OUTPUT_DIR / "local-dataset"
BUCKETS_DIR = LOCAL_DATASET_OUTPUT_DIR / "buckets"
MANIFEST_FILE = LOCAL_DATASET_OUTPUT_DIR / "manifest.json"
BUCKET_COUNT = 100

# Set to a small integer for smoke tests. Use None for the full dataset.
MAX_ROWS_TO_WRITE = None

OUTPUT_COLUMNS = [
    "code",
    "brands",
    "name_candidates",
    "product_quantity",
    "product_quantity_unit",
    "package_quantity",
    "package_quantity_unit",
    "serving_quantity",
    "serving_quantity_unit",
    # "g" means nutrition values are standardized per 100g.
    # "ml" means nutrition values are standardized per 100ml.
    "nutrition_basis_unit",
    "nutrition",
    "image_1",
    "image_2",
]

# The raw compact CSV produced by main.py does not have product_index yet.
# product_index is assigned by bucket_writer.py after sorting rows by barcode.
INDEXED_OUTPUT_COLUMNS = [PRODUCT_INDEX_COLUMN, *OUTPUT_COLUMNS]

NAME_COLUMNS = [
    "product_name",
    "product_name_en",
    "product_name_fr",
    "product_name_de",
    "product_name_it",
    "generic_name",
    "generic_name_en",
    "generic_name_fr",
    "generic_name_it",
    "generic_name_hr",
]
