# Local Dataset Builder

## Note

This helper program does **not** run as part of the Spring Boot server.

It is included in the repository as an offline add-on tool for dataset generation and for contribution consideration. The server only reads the generated dataset files at runtime.

## What it generates
This helper program prepares the local Open Food Facts dataset used by the backend product lookup system.

It reads the raw Open Food Facts JSONL dump, extracts the product fields needed by the pantry app, compacts nutrition-related data, and generates the local CSV bucket files plus the manifest used by the Spring Boot server.

The generated dataset is intended to support offline/local barcode lookup without relying on the live Open Food Facts API.

The builder produces:

- `local_product_dataset.csv`
- `manifest.json`
- `buckets/bucket_000.csv` through `buckets/bucket_099.csv`

These files are used by the backend local dataset lookup code.

## Main Extracted fields

The builder extracts and prepares:

- barcode/code
- brand
- candidate product names
- product quantity and serving quantity
- kcal information
- compact `nutrition` data
- compact `nutriments` data
- image revision metadata

