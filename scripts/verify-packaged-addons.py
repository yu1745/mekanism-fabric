#!/usr/bin/env python3
"""Validate the optional addon JARs against the core release JAR.

Usage: verify-packaged-addons.py CORE_JAR ADDON_JAR [ADDON_JAR ...]

Exit 0 only when exactly the three expected addon ids are present, each carrying the core version, depending on that
same version and shipping no nested jars; otherwise exit 1 with the first violation on stderr. The release smoke gate
and its negative probes run this same file, so a probe failure proves the gate is not a no-op.
"""
import json
import sys
import zipfile

EXPECTED_IDS = {"mekanismtools", "mekanismadditions", "mekanismgenerators"}


def fail(message):
    print(f"verify-packaged-addons: {message}", file=sys.stderr)
    raise SystemExit(1)


def main(argv):
    if len(argv) < 2:
        fail("usage: verify-packaged-addons.py CORE_JAR ADDON_JAR [ADDON_JAR ...]")
    core_path, addon_paths = argv[1], argv[2:]
    if len(addon_paths) != 3:
        fail(f"expected exactly three optional addon JARs, found {len(addon_paths)}")
    with zipfile.ZipFile(core_path) as core:
        version = json.loads(core.read("fabric.mod.json"))["version"]
    ids = set()
    for path in addon_paths:
        with zipfile.ZipFile(path) as addon:
            metadata = json.loads(addon.read("fabric.mod.json"))
        if metadata.get("version") != version or metadata.get("depends", {}).get("mekanism") != version or metadata.get("jars"):
            fail(f"addon version, core dependency or isolation contract changed: {path}")
        if metadata["id"] in ids:
            fail(f"duplicate addon id: {metadata['id']}")
        ids.add(metadata["id"])
    if ids != EXPECTED_IDS:
        fail(f"unexpected addon ids: {ids}")
    print(f"verify-packaged-addons: ok version={version} ids={','.join(sorted(ids))}")


if __name__ == "__main__":
    main(sys.argv)
