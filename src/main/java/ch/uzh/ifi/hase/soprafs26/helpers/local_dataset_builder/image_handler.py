from typing import Any

from local_dataset_builder.text_utils import clean_scalar, ensure_mapping


def extract_image_1(images_value: Any) -> dict[str, str]:
    images = ensure_mapping(images_value)

    if not images:
        return {}

    selected = images.get("selected")

    if not isinstance(selected, dict):
        return {}

    front = selected.get("front")

    if not isinstance(front, dict):
        return {}

    revisions_by_language: dict[str, str] = {}

    for language, image_info in front.items():
        if not isinstance(image_info, dict):
            continue

        language_key = clean_scalar(language)
        revision = clean_scalar(image_info.get("rev"))

        if language_key and revision:
            revisions_by_language[language_key] = revision

    return revisions_by_language


def extract_image_2(images_value: Any) -> dict[str, str]:
    images = ensure_mapping(images_value)

    if not images:
        return {}

    revisions_by_language: dict[str, str] = {}

    for key, image_info in images.items():
        if not isinstance(image_info, dict):
            continue

        key_name = clean_scalar(key)
        revision = clean_scalar(image_info.get("rev"))

        if not key_name or not revision:
            continue

        if key_name == "front":
            revisions_by_language["default"] = revision
        elif key_name.startswith("front_"):
            language = clean_scalar(key_name.removeprefix("front_"))

            if language:
                revisions_by_language[language] = revision

    return revisions_by_language