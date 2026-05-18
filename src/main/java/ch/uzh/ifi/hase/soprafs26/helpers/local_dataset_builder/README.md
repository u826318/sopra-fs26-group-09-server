# Local Dataset Builder

This helper builds a compact local Open Food Facts dataset for the Pantry backend.

## Output schema

The compact CSV contains:

```text
code
brands
name_candidates
product_quantity
product_quantity_unit
package_quantity
package_quantity_unit
serving_quantity
serving_quantity_unit
nutrition_basis_unit
nutrition
image_1
image_2
```

`product_quantity` / `product_quantity_unit` are the raw Open Food Facts package-size fields.

`package_quantity` / `package_quantity_unit` are normalized package quantity when available:

```text
package_quantity_unit = g or ml
```

`serving_quantity` / `serving_quantity_unit` are normalized serving quantity when available:

```text
serving_quantity_unit = g or ml
```

`nutrition_basis_unit` tells what the values inside `nutrition` are per:

```text
g  = nutrient values are per 100g
ml = nutrient values are per 100ml
```

## Nutrition cell schema

The `nutrition` cell is a flat indexed JSON dictionary:

```json
{"0":533.0,"1":30.9,"13":3300.0}
```

Nutrient keys are numeric indexes defined in `nutrient_index.py`.

Unit meanings after standardization:

- `energy-kcal`: kcal per 100g/ml
- core nutrition keys: grams per 100g/ml
- micronutrient keys: micrograms per 100g/ml

This version intentionally converts per-serving values to per-100g or per-100ml when normalized serving quantity is available. If a source only provides `*_value` / `*_unit` without a clean basis, the builder preserves it as an assumed per-100 value and infers whether that should be per 100g or per 100ml from package/serving metadata. If no useful clue exists, it defaults to per 100g to preserve the row.

The builder does **not** force per-package calculation. It preserves standardized per-100 data and records package quantity when present, so the backend can calculate per-package or consumed nutrients later.


## Indexed bucket output

`python -m local_dataset_builder.main` writes the unsorted compact CSV:

```text
local_dataset_builder_outputs/local_dataset.csv
```

`python -m local_dataset_builder.bucket_writer` then:

1. reads `local_dataset.csv`,
2. filters rows with numeric barcodes,
3. sorts them by barcode ascending,
4. assigns a stable 1-based `product_index`,
5. writes a full sorted indexed CSV,
6. writes bucket CSVs that also contain `product_index`, and
7. writes manifest ranges for both barcode and product index.

The full sorted indexed CSV is:

```text
local_dataset_builder_outputs/local_dataset_indexed.csv
```

Bucket rows now start with:

```text
product_index,code,brands,...
```

`product_index` is assigned like this:

```text
1 = smallest barcode row
2 = second-smallest barcode row
...
```

The manifest now includes both barcode ranges and product-index ranges:

```json
{
  "product_index_assignment": "barcode_ascending_1_based",
  "global_min_product_index": 1,
  "global_max_product_index": 4475128,
  "buckets": [
    {
      "bucket_id": 0,
      "filename": "bucket_000.csv",
      "min_code": "0000000000012",
      "max_code": "1234567890000",
      "min_product_index": 1,
      "max_product_index": 44752,
      "row_count": 44752
    }
  ]
}
```

This lets the backend resolve either:

```text
barcode -> bucket -> product row
```

or:

```text
product_index -> bucket -> product row
```

The name-matching SQLite index should store `product_index` values generated from the same dataset build.

## File placement

Keep the raw Open Food Facts dump in the **parent directory of the helper package**.

Recommended layout:

```text
backend/helpers/
├── openfoodfacts-products.jsonl.gz
├── local_dataset_builder/
│   ├── main.py
│   ├── config.py
│   ├── row_builder.py
│   └── ...
└── local_dataset_builder_outputs/
    ├── local_dataset.csv
    └── local-dataset/
        ├── manifest.json
        └── buckets/
```

In other words, if this package is here:

```text
backend/helpers/local_dataset_builder/
```

then put the raw dump here:

```text
backend/helpers/openfoodfacts-products.jsonl.gz
```

By default, generated outputs are written here:

```text
backend/helpers/local_dataset_builder_outputs/
```

## Optional path override

If your raw dump is somewhere else, you can override paths without editing code:

```bash
LOCAL_DATASET_DIR="/path/to/dataset-dir" python -m local_dataset_builder.main
```

or:

```bash
OFF_INPUT_FILE="/path/to/openfoodfacts-products.jsonl.gz" python -m local_dataset_builder.main
```

## Run

From the directory that contains `local_dataset_builder/`, for example `backend/helpers`:

```bash
python -m local_dataset_builder.main
python -m local_dataset_builder.bucket_writer
```

For a smoke test, temporarily set `MAX_ROWS_TO_WRITE` in `config.py` to a small integer. For the full dataset, keep:

```python
MAX_ROWS_TO_WRITE = None
```
