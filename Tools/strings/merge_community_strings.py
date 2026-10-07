"""Merge reviewed English/Spanish feature bundles into Telegram's string tables."""
import json
import re
from pathlib import Path
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[2]


def merge():
    for language, folder in (("en", "values"), ("es", "values-es")):
        strings = {}
        for name in ("community-root-strings.json", "community-phrases-downloads-strings.json"):
            for key, translations in json.loads((ROOT / "Tools" / name).read_text(encoding="utf-8-sig")).items():
                value = escape(translations[language]).replace("'", "\\'")
                strings[key] = f'<string name="{key}">{value}</string>'
        bundles = [ROOT / f"Tools/reading/strings-{language}.xml"]
        bundles += sorted((ROOT / "Tools/strings").glob(f"*-{language}.xml"))
        if language == "es":
            bundles += [ROOT / "Tools/strings/diagnostics-updates.xml"]
        for path in bundles:
            source = path.read_text(encoding="utf-8-sig")
            ET.fromstring(source)
            for match in re.finditer(r'<string\s+name="([^"]+)"[^>]*>.*?</string>', source, re.S):
                strings[match[1]] = match[0]
        target = ROOT / f"TMessagesProj/src/main/res/{folder}/strings.xml"
        original = target.read_text(encoding="utf-8")
        updated = original
        for key, element in strings.items():
            pattern = r'<string\s+name="' + re.escape(key) + r'"[^>]*>.*?</string>'
            updated, count = re.subn(pattern, lambda _: element, updated, flags=re.S)
            if count > 1:
                raise ValueError(f"Duplicate resource {key} in {target}")
            if not count:
                updated = updated.replace("</resources>", f"    {element}\n</resources>")
        ET.fromstring(updated)
        if updated != original:
            target.write_text(updated, encoding="utf-8")
        print(f"{language}: merged {len(strings)} strings")


if __name__ == "__main__":
    merge()
