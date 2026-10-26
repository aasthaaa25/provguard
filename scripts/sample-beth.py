#!/usr/bin/env python3
"""Draw a deterministic sample from the Kaggle BETH process logs.

Source: https://www.kaggle.com/datasets/katehighnam/beth-dataset
Place labelled_training_data.csv, labelled_validation_data.csv, and
labelled_testing_data.csv in the directory passed as the first argument.
The evil and sus columns are kept only as labels. They are not features.
"""

import ast
import csv
import random
import sys
from pathlib import Path

USERSPACE_LIMIT = 0x0000800000000000
KERNEL_FLOOR = 0xFFFF000000000000
TRAIN_PER_HOST = 280
VALIDATION_PER_HOST = 120
EVIL_ROWS = 500
TEST_BENIGN_ROWS = 200


def clean(text: str) -> str:
    return " ".join(text.replace(",", " ").split())


def stack_counts(raw: str) -> tuple[int, int, int]:
    if not raw or raw == "[]":
        return 0, 0, 0
    try:
        values = ast.literal_eval(raw)
    except (SyntaxError, ValueError):
        return 0, 0, 0
    if not isinstance(values, list):
        return 0, 0, 0
    userspace = 0
    kernel = 0
    other = 0
    for value in values:
        if not isinstance(value, int):
            other += 1
            continue
        if value < USERSPACE_LIMIT:
            userspace += 1
        elif value >= KERNEL_FLOOR:
            kernel += 1
        else:
            other += 1
    return len(values), userspace, other + kernel


def argument_parts(raw: str) -> tuple[int, int, str]:
    names: set[str] = set()
    values: list[str] = []
    marker = "'name':"
    value_marker = "'value':"
    for piece in raw.split("},"):
        name_at = piece.find(marker)
        if name_at >= 0:
            quoted = piece[name_at + len(marker):].strip()
            if quoted.startswith("'"):
                end = quoted.find("'", 1)
                if end > 1:
                    names.add(quoted[1:end])
        value_at = piece.find(value_marker)
        if value_at >= 0:
            quoted = piece[value_at + len(value_marker):].strip().rstrip("}]")
            if quoted.startswith("'"):
                end = quoted.find("'", 1)
                if end >= 1:
                    values.append(quoted[1:end])
            else:
                token = quoted.split(",", 1)[0].strip()
                if token:
                    values.append(token)
    joined = " ".join(values)
    return len(joined), len(names), clean(joined)[:180]


def remember(buckets: dict, key: str, row: dict, limit: int, rng: random.Random) -> None:
    seen, rows = buckets.setdefault(key, [0, []])
    seen += 1
    buckets[key][0] = seen
    if len(rows) < limit:
        rows.append(row)
    else:
        slot = rng.randrange(seen)
        if slot < limit:
            rows[slot] = row


def convert(row: dict, family: str, label: str, split: str) -> dict:
    stack_len, userspace, other = stack_counts(row["stackAddresses"])
    argument_length, distinct_names, values = argument_parts(row["args"])
    text = clean(f"{row['processName']} {row['eventName']} {values}")[:220]
    return {
        "family": family,
        "label": label,
        "split": split,
        "host": clean(row["hostName"]),
        "process_name": clean(row["processName"]),
        "event_name": clean(row["eventName"]),
        "stack_len": stack_len,
        "userspace_frames": userspace,
        "other_frames": other,
        "argument_length": argument_length,
        "distinct_arg_names": distinct_names,
        "return_value": int(row["returnValue"]),
        "user_id": int(row["userId"]),
        "text": text,
    }


def stream(path: Path, predicate, family_for, label: str, split: str, limit: int, rng: random.Random):
    buckets: dict = {}
    with path.open(newline="") as handle:
        for raw in csv.DictReader(handle):
            if not predicate(raw):
                continue
            family = family_for(raw)
            remember(buckets, family, convert(raw, family, label, split), limit, rng)
    rows = []
    for _family, (_seen, picked) in buckets.items():
        rows.extend(picked)
    return rows


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: sample-beth.py RAW_DIR OUTPUT_CSV")
    raw = Path(sys.argv[1])
    output = Path(sys.argv[2])
    rng = random.Random(42)
    rows = []
    rows.extend(stream(
        raw / "labelled_training_data.csv",
        lambda row: row["evil"] == "0" and row["sus"] == "0",
        lambda row: "beth-" + row["hostName"],
        "BENIGN",
        "train",
        TRAIN_PER_HOST,
        rng,
    ))
    rows.extend(stream(
        raw / "labelled_validation_data.csv",
        lambda row: row["evil"] == "0" and row["sus"] == "0",
        lambda row: "beth-val-" + row["hostName"],
        "BENIGN",
        "test",
        VALIDATION_PER_HOST,
        rng,
    ))
    rows.extend(stream(
        raw / "labelled_testing_data.csv",
        lambda row: row["evil"] == "0" and row["sus"] == "0",
        lambda _row: "beth-test-benign",
        "BENIGN",
        "test",
        TEST_BENIGN_ROWS,
        rng,
    ))
    rows.extend(stream(
        raw / "labelled_testing_data.csv",
        lambda row: row["evil"] == "1",
        lambda _row: "beth-evil",
        "SUSPICIOUS",
        "test",
        EVIL_ROWS,
        rng,
    ))
    output.parent.mkdir(parents=True, exist_ok=True)
    fields = list(rows[0].keys())
    with output.open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    print(f"wrote {len(rows)} rows to {output}")


if __name__ == "__main__":
    main()
