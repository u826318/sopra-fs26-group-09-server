from local_dataset_builder.config import INPUT_FILE, MAX_ROWS_TO_WRITE, OUTPUT_FILE
from local_dataset_builder.dataset_writer import write_dataset


def main() -> None:
    print(f"Input file : {INPUT_FILE.resolve()}")
    print(f"Output file: {OUTPUT_FILE.resolve()}")
    print(f"Max rows   : {MAX_ROWS_TO_WRITE}")

    if not INPUT_FILE.exists():
        print("\nInput file does not exist yet.")
        print(f"Put openfoodfacts-products.jsonl.gz here: {INPUT_FILE.parent.resolve()}")
        return

    write_dataset(INPUT_FILE, OUTPUT_FILE, MAX_ROWS_TO_WRITE)


if __name__ == "__main__":
    main()
