#!/usr/bin/env python3
"""W-75: compare the visual/data resources of the official NeoForge JARs against the Fabric release JARs.

Both sides are keyed by archive path. JSON files are normalised (parse, sort keys, compact dump) before hashing so the
port's pretty-printed datagen output does not show up as a difference; every other file is compared byte for byte.

Usage: scripts/compare-resource-inventory.py [--official DIR] [--fabric DIR] [--json OUT]
Exit code 1 when a path is missing from the Fabric side, has different content, or the upstream file is not JSON and
differs byte-wise. Extra Fabric paths are reported but do not fail: they are the port's own generated data.
"""
import argparse
import hashlib
import json
import os
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_OFFICIAL = os.path.expanduser("~/.cache/mekanism-neoforge-reference/official")
DEFAULT_FABRIC = os.path.join(ROOT, "dist")
# Upstream files that belong to another loader / optional integration and are expected to be absent or replaced.
PLATFORM_PREFIXES = ("data/neoforge/", "data/projecte/", "assets/emi/", "data/mekanism/computer_help/")
PLATFORM_FILES = {"assets/mekanism/convert_uv.py"}


def normalise(raw):
    if not raw.lstrip().startswith((b"{", b"[")):
        return None
    try:
        parsed = json.loads(raw)
    except (ValueError, UnicodeDecodeError):
        return None
    return hashlib.sha256(json.dumps(parsed, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def collect(directory):
    entries = {}
    for name in sorted(os.listdir(directory)):
        if not name.endswith(".jar") or "-api" in name or "sources" in name:
            continue
        with zipfile.ZipFile(os.path.join(directory, name)) as archive:
            for info in archive.infolist():
                if info.is_dir() or not info.filename.startswith(("data/", "assets/")):
                    continue
                raw = archive.read(info)
                entries[info.filename] = {
                    "jar": name,
                    "raw": hashlib.sha256(raw).hexdigest(),
                    "normalised": normalise(raw),
                }
    return entries


def is_platform(path):
    return path.startswith(PLATFORM_PREFIXES) or path in PLATFORM_FILES


def main(argv):
    parser = argparse.ArgumentParser()
    parser.add_argument("--official", default=DEFAULT_OFFICIAL)
    parser.add_argument("--fabric", default=DEFAULT_FABRIC)
    parser.add_argument("--json")
    args = parser.parse_args(argv[1:])

    official = collect(args.official)
    fabric = collect(args.fabric)
    missing, content_diff, formatting_only, extra = [], [], [], []
    for path, data in official.items():
        other = fabric.get(path)
        if other is None:
            (extra if is_platform(path) else missing).append({"path": path, "official": data["jar"]})
            continue
        if data["raw"] == other["raw"]:
            continue
        if data["normalised"] is not None and data["normalised"] == other["normalised"]:
            formatting_only.append(path)
        else:
            content_diff.append({"path": path, "official": data["jar"], "fabric": other["jar"]})
    for path in sorted(set(fabric) - set(official)):
        if not is_platform(path):
            extra.append({"path": path, "fabric": fabric[path]["jar"]})

    extra.sort(key=lambda item: item["path"])
    report = {
        "official_entries": len(official),
        "fabric_entries": len(fabric),
        "identical": len(official) - len(missing) - len(content_diff) - len(formatting_only),
        "formatting_only": len(formatting_only),
        "missing_in_fabric": missing,
        "content_differs": content_diff,
        "fabric_only": extra,
    }
    print(json.dumps({k: report[k] for k in ("official_entries", "fabric_entries", "identical", "formatting_only")},
                     indent=1))
    for label, key in (("missing in fabric", "missing_in_fabric"), ("content differs", "content_differs")):
        print(f"{label}: {len(report[key])}")
        for item in report[key][:15]:
            print("  " + item["path"])
    print(f"fabric-only (not a failure): {len(report['fabric_only'])}")
    for item in report["fabric_only"][:10]:
        print("  " + item["path"])
    if args.json:
        with open(args.json, "w") as handle:
            json.dump(report, handle, indent=1)
            handle.write("\n")
    return 1 if missing or content_diff else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
