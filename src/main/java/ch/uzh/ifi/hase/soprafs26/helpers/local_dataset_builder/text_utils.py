import json
from typing import Any


def clean_scalar(value: Any) -> str:
    if value is None:
        return ""

    text = str(value).strip()

    return text


def csv_safe(value: Any) -> str:
    if value is None:
        return ""

    if isinstance(value, (dict, list)):
        return json.dumps(value, ensure_ascii=False, sort_keys=True)

    text = str(value)
    stripped = text.strip()

    return stripped


def ensure_mapping(value: Any) -> dict[str, Any] | None:
    if isinstance(value, dict):
        return value

    if isinstance(value, str):
        raw = clean_scalar(value)

        if not raw:
            return None

        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError:
            return None

        return parsed if isinstance(parsed, dict) else None

    return None