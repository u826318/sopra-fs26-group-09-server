from local_dataset_builder.config import INPUT_FILE, OUTPUT_FILE, MAX_ROWS_TO_WRITE


def main() -> None:
    print(f"Input file : {INPUT_FILE.resolve()}")
    print(f"Output file: {OUTPUT_FILE.resolve()}")
    print(f"Max rows   : {MAX_ROWS_TO_WRITE}")

    if not INPUT_FILE.exists():
        print("\nInput file does not exist yet.")
        print("Put openfoodfacts-products.jsonl.gz in helper-scripts/.")
        return

    print("\nInput file found. Package wiring works.")


if __name__ == "__main__":
    main()