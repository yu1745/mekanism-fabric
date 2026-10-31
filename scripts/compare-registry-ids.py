#!/usr/bin/env python3
"""W-76: compare the registry identifiers that each side actually ships.

A full runtime dump would need a mod-side command (mcdebug has no registry RPC: its only registry class is the
RegistriesIdLookup.typeIdOf helper), so this compares the data the game resolves ids from:
  blocks - assets/<ns>/blockstates/<name>.json
  items  - assets/<ns>/models/item/<name>.json
  tags   - data/<ns>/tags/<kind>/<name>.json

Missing entries in the mod's own namespaces fail the run; foreign-namespace entries (neoforge:, projecte:,
accessories:) are reported separately because they belong to the platform or to optional integrations.

Usage: scripts/compare-registry-ids.py [--official DIR] [--fabric DIR] [--json OUT]
"""
import argparse
import glob
import json
import os
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_OFFICIAL = os.path.expanduser("~/.cache/mekanism-neoforge-reference/official")
DEFAULT_FABRIC = os.path.join(ROOT, "dist")
OWN_NAMESPACES = {"mekanism", "mekanismtools", "mekanismadditions", "mekanismgenerators"}


def inventory(jars):
    out = {"blocks": set(), "items": set(), "tags": set()}
    for jar in jars:
        with zipfile.ZipFile(jar) as archive:
            for name in archive.namelist():
                if name.startswith("assets/") and "/blockstates/" in name and name.endswith(".json"):
                    namespace = name.split("/")[1]
                    out["blocks"].add(f"{namespace}:{name.split('/blockstates/')[1][:-5]}")
                elif name.startswith("assets/") and "/models/item/" in name and name.endswith(".json"):
                    namespace = name.split("/")[1]
                    out["items"].add(f"{namespace}:{name.split('/models/item/')[1][:-5]}")
                elif name.startswith("data/") and "/tags/" in name and name.endswith(".json"):
                    parts = name.split("/")
                    if len(parts) > 4:
                        out["tags"].add(f"{parts[1]}:{parts[3]}/{'/'.join(parts[4:])[:-5]}")
    return out


def main(argv):
    parser = argparse.ArgumentParser()
    parser.add_argument("--official", default=DEFAULT_OFFICIAL)
    parser.add_argument("--fabric", default=DEFAULT_FABRIC)
    parser.add_argument("--json")
    args = parser.parse_args(argv[1:])

    official = inventory(sorted(glob.glob(os.path.join(args.official, "*.jar"))))
    fabric = inventory(sorted(glob.glob(os.path.join(args.fabric, "*.jar"))))

    report = {}
    own_gaps = 0
    for kind in ("blocks", "items", "tags"):
        missing = sorted(official[kind] - fabric[kind])
        extra = sorted(fabric[kind] - official[kind])
        own_missing = [i for i in missing if i.split(":", 1)[0] in OWN_NAMESPACES]
        own_extra = [i for i in extra if i.split(":", 1)[0] in OWN_NAMESPACES]
        own_gaps += len(own_missing) + len(own_extra)
        report[kind] = {
            "official": len(official[kind]),
            "fabric": len(fabric[kind]),
            "missing": missing,
            "missing_own_namespace": own_missing,
            "extra": extra,
            "extra_own_namespace": own_extra,
        }
        print(f"{kind}: official={len(official[kind])} fabric={len(fabric[kind])} "
              f"missing={len(missing)} (own {len(own_missing)}) extra={len(extra)} (own {len(own_extra)})")
        for item in missing[:6]:
            print(f"   missing {item}")
        for item in extra[:6]:
            print(f"   extra   {item}")

    if args.json:
        with open(args.json, "w") as handle:
            json.dump(report, handle, indent=1)
            handle.write("\n")
    return 1 if own_gaps else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
