#!/usr/bin/env python3
"""Compare resource names in Telegram Crawler, upstream Android and DEV."""

import argparse
import json
import xml.etree.ElementTree as ET
from pathlib import Path


def resource_keys(path: Path) -> set[str]:
    root = ET.parse(path).getroot()
    if root.tag != "resources":
        raise ValueError(f"Expected an Android resources file: {path}")
    keys = set()
    for entry in root:
        name = entry.get("name")
        kind = entry.get("type") if entry.tag == "item" else entry.tag
        if name and kind in {"string", "plurals", "string-array"}:
            keys.add(f"{kind}/{name}")
    if not keys:
        raise ValueError(f"No supported resource names found: {path}")
    return keys


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("beta", type=Path, help="Telegram Crawler Android beta strings.xml")
    parser.add_argument("official", type=Path, help="Official Telegram Android strings.xml")
    parser.add_argument("dev", type=Path, help="Comunidad Telebots DEV strings.xml")
    args = parser.parse_args()

    try:
        beta = resource_keys(args.beta)
        official = resource_keys(args.official)
        dev = resource_keys(args.dev)
    except (OSError, ET.ParseError, ValueError) as exc:
        parser.exit(2, f"{exc}\n")

    print(json.dumps({
        "beta_candidates": sorted(beta - official - dev),
        "official_backport_candidates": sorted((beta & official) - dev),
        "note": "Resource names are clues. Check upstream code and runtime behavior before porting.",
    }, indent=2))


if __name__ == "__main__":
    main()
