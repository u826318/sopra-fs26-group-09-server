from pathlib import Path


PACKAGE_DIR = Path(__file__).resolve().parent
LOCAL_DATASET_DIR = PACKAGE_DIR.parent
HELPER_SCRIPTS_DIR = LOCAL_DATASET_DIR.parent

INPUT_FILE = HELPER_SCRIPTS_DIR / "openfoodfacts-products.jsonl.gz"
OUTPUT_FILE = LOCAL_DATASET_DIR / "compact_relevant_preview.csv"

MAX_ROWS_TO_WRITE = 5

OUTPUT_COLUMNS = [
    "code",
    "brands",
    "name_candidates",
    "product_quantity",
    "product_quantity_unit",
    "serving_quantity",
    "serving_quantity_unit",
    "energy_kcal_value",
    "energy_kcal_basis",
    "energy_kcal_unit",
    "nutrition_compact",
    "nutriments_compact",
    "image_1",
    "image_2",
]

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

NUTRIENT_KEYS_TO_KEEP = [
    "energy-kcal",
    "energy-kj",
    "fat",
    "saturated-fat",
    "carbohydrates",
    "sugars",
    "fiber",
    "proteins",
    "salt",
    "sodium",

    "calcium",
    "choline",
    "copper",
    "iodine",
    "iron",
    "magnesium",
    "manganese",
    "phosphorus",
    "potassium",
    "selenium",
    "zinc",

    "vitamin-a",
    "vitamin-b1",
    "vitamin-b2",
    "vitamin-b6",
    "vitamin-b12",
    "vitamin-c",
    "vitamin-d",
    "vitamin-e",
    "vitamin-k",
    "vitamin-b9",
    "vitamin-pp",
    "pantothenic-acid",

    "biotin",
    "chloride",
    "chromium",
    "fluoride",
    "molybdenum",
]