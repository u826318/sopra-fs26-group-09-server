import csv
import json
import math
import shutil
from pathlib import Path
from typing import Iterator

from local_dataset_builder.config import (
    BUCKET_COUNT,
    BUCKETS_DIR,
    INDEXED_OUTPUT_COLUMNS,
    INDEXED_OUTPUT_FILE,
    LOCAL_DATASET_OUTPUT_DIR,
    MANIFEST_FILE,
    OUTPUT_COLUMNS,
    OUTPUT_FILE,
    PRODUCT_INDEX_COLUMN,
)


def _nul_stripped_lines(handle) -> Iterator[str]:
    for line in handle:
        yield line.replace("\x00", "")


def _read_valid_rows(input_file: Path) -> list[tuple[int, dict[str, str]]]:
    rows: list[tuple[int, dict[str, str]]] = []

    with input_file.open("r", encoding="utf-8-sig", errors="replace", newline="") as handle:
        reader = csv.DictReader(_nul_stripped_lines(handle))

        for row in reader:
            code = (row.get("code") or "").strip()

            if not code.isdigit():
                continue

            normalized_row = {column: row.get(column, "") for column in OUTPUT_COLUMNS}
            rows.append((int(code), normalized_row))

    rows.sort(key=lambda item: item[0])
    return rows


def _assign_product_indexes(
    sorted_rows: list[tuple[int, dict[str, str]]],
) -> list[tuple[int, int, dict[str, str]]]:
    """Assign stable 1-based product_index values after barcode sorting.

    The index is intentionally tied to the barcode-ascending order. This lets
    the name-matching index store compact integer product indexes and later
    resolve them through the same bucket/manifest structure used for barcodes.
    """
    indexed_rows: list[tuple[int, int, dict[str, str]]] = []

    for product_index, (code_as_int, row) in enumerate(sorted_rows, start=1):
        indexed_row = {column: row.get(column, "") for column in OUTPUT_COLUMNS}
        indexed_row[PRODUCT_INDEX_COLUMN] = str(product_index)
        indexed_rows.append((product_index, code_as_int, indexed_row))

    return indexed_rows


def _write_indexed_master_csv(
    indexed_rows: list[tuple[int, int, dict[str, str]]],
    indexed_output_file: Path,
) -> None:
    indexed_output_file.parent.mkdir(parents=True, exist_ok=True)

    with indexed_output_file.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=INDEXED_OUTPUT_COLUMNS, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(row for _, _, row in indexed_rows)


def write_buckets(
    input_file: Path = OUTPUT_FILE,
    indexed_output_file: Path = INDEXED_OUTPUT_FILE,
    output_dir: Path = LOCAL_DATASET_OUTPUT_DIR,
    buckets_dir: Path = BUCKETS_DIR,
    manifest_file: Path = MANIFEST_FILE,
    bucket_count: int = BUCKET_COUNT,
) -> dict:
    """Split local_dataset.csv into barcode-sorted indexed buckets.

    This step assigns product_index values after sorting by barcode, writes a
    full sorted indexed CSV, writes bucket CSVs that include product_index, and
    writes manifest.json with both barcode ranges and product_index ranges.
    """
    if not input_file.exists():
        raise FileNotFoundError(f"Compact dataset does not exist: {input_file.resolve()}")

    if output_dir.exists():
        shutil.rmtree(output_dir)

    buckets_dir.mkdir(parents=True, exist_ok=True)

    sorted_rows = _read_valid_rows(input_file)
    indexed_rows = _assign_product_indexes(sorted_rows)

    manifest = {
        "source_file": str(input_file.name),
        "indexed_source_file": str(indexed_output_file.name),
        "bucket_count": 0,
        "product_index_base": 1,
        "product_index_assignment": "barcode_ascending_1_based",
        "global_min_code": None,
        "global_max_code": None,
        "global_min_product_index": None,
        "global_max_product_index": None,
        "buckets": [],
    }

    if not indexed_rows:
        output_dir.mkdir(parents=True, exist_ok=True)
        _write_indexed_master_csv(indexed_rows, indexed_output_file)
        manifest_file.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
        print("No valid barcode rows found; wrote empty manifest and empty indexed dataset.")
        print(f"Indexed CSV  : {indexed_output_file.resolve()}")
        print(f"Manifest file: {manifest_file.resolve()}")
        return manifest

    actual_bucket_count = min(bucket_count, len(indexed_rows))
    bucket_size = math.ceil(len(indexed_rows) / actual_bucket_count)

    manifest["bucket_count"] = actual_bucket_count
    manifest["global_min_code"] = str(indexed_rows[0][1])
    manifest["global_max_code"] = str(indexed_rows[-1][1])
    manifest["global_min_product_index"] = indexed_rows[0][0]
    manifest["global_max_product_index"] = indexed_rows[-1][0]

    _write_indexed_master_csv(indexed_rows, indexed_output_file)

    for bucket_id in range(actual_bucket_count):
        start = bucket_id * bucket_size
        end = min(start + bucket_size, len(indexed_rows))
        bucket_rows = indexed_rows[start:end]

        if not bucket_rows:
            continue

        filename = f"bucket_{bucket_id:03d}.csv"
        bucket_path = buckets_dir / filename

        with bucket_path.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=INDEXED_OUTPUT_COLUMNS, extrasaction="ignore")
            writer.writeheader()
            writer.writerows(row for _, _, row in bucket_rows)

        manifest["buckets"].append(
            {
                "bucket_id": bucket_id,
                "filename": filename,
                "min_code": str(bucket_rows[0][1]),
                "max_code": str(bucket_rows[-1][1]),
                "min_product_index": bucket_rows[0][0],
                "max_product_index": bucket_rows[-1][0],
                "row_count": len(bucket_rows),
            }
        )

    output_dir.mkdir(parents=True, exist_ok=True)
    manifest_file.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"Finished bucket build. Rows: {len(indexed_rows):,} | buckets: {len(manifest['buckets']):,}")
    print(f"Indexed CSV  : {indexed_output_file.resolve()}")
    print(f"Manifest file: {manifest_file.resolve()}")
    print(f"Buckets dir  : {buckets_dir.resolve()}")

    return manifest


if __name__ == "__main__":
    write_buckets()
