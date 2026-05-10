from typing import Any

from local_dataset_builder.config import NAME_COLUMNS
from local_dataset_builder.text_utils import clean_scalar


def extract_name_candidates(product: dict[str, Any]) -> list[str]:
    candidates: list[str] = []
    seen: set[str] = set()

    for column in NAME_COLUMNS:
        name = clean_scalar(product.get(column))

        if not name:
            continue

        normalized_name = name.casefold()

        if normalized_name in seen:
            continue

        candidates.append(name)
        seen.add(normalized_name)

    return candidates