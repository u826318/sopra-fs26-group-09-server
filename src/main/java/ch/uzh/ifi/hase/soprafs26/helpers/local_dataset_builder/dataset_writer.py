import csv
import gzip
import json
from pathlib import Path
from typing import Any, Iterator, TextIO

from local_dataset_builder.config import INPUT_FILE, MAX_ROWS_TO_WRITE, OUTPUT_COLUMNS, OUTPUT_FILE
from local_dataset_builder.row_builder import build_preview_row, has_minimum_product_data


def _open_text(path: Path) -> TextIO:
    if path.suffix == ".gz":
        return gzip.open(path, "rt", encoding="utf-8", errors="replace", newline="")

    return path.open("r", encoding="utf-8", errors="replace", newline="")


def _iter_products(input_file: Path) -> Iterator[dict[str, Any]]:
    with _open_text(input_file) as handle:
        for line_number, line in enumerate(handle, start=1):
            clean_line = line.replace("\x00", "").strip()

            if not clean_line:
                continue

            try:
                product = json.loads(clean_line)
            except json.JSONDecodeError:
                continue

            if isinstance(product, dict):
                yield product


def write_dataset(
    input_file: Path = INPUT_FILE,
    output_file: Path = OUTPUT_FILE,
    max_rows_to_write: int | None = MAX_ROWS_TO_WRITE,
) -> int:
    """Build the compact local dataset CSV from the raw OFF JSONL dump."""
    if not input_file.exists():
        raise FileNotFoundError(f"Input file does not exist: {input_file.resolve()}")

    output_file.parent.mkdir(parents=True, exist_ok=True)

    written_count = 0
    scanned_count = 0

    with output_file.open("w", encoding="utf-8", newline="") as output_handle:
        writer = csv.DictWriter(output_handle, fieldnames=OUTPUT_COLUMNS, extrasaction="ignore")
        writer.writeheader()

        for product in _iter_products(input_file):
            scanned_count += 1

            if not has_minimum_product_data(product):
                continue

            row = build_preview_row(product)
            writer.writerow(row)
            written_count += 1

            if max_rows_to_write is not None and written_count >= max_rows_to_write:
                break

            if written_count and written_count % 100_000 == 0:
                print(f"Written rows: {written_count:,} | scanned products: {scanned_count:,}")

    print(f"Finished dataset build. Written rows: {written_count:,}")
    print(f"Output file: {output_file.resolve()}")

    return written_count
