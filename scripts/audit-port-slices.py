#!/usr/bin/env python3
"""Systematic audit of the port's admission slices.

Checks, in order:
  1. Every admitted path exists in its tree, no duplicates.
  2. Cross-root dangling imports: no admitted file imports a type that exists in the
     source trees but is NOT admitted (a silently-compiling hole).
  3. Every mixin declared in mekanism.mixins.json is admitted to the platform slice.
  4. Entry point classes referenced by fabric.mod.json exist on disk.
Exit code 0 when all checks pass, 1 otherwise.
"""
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TREES = [
    ("src/main/java", "fabric/common-slice.txt"),
    ("src/api/java", "fabric/api-slice.txt"),
    ("fabric/src/main/java", "fabric/platform-slice.txt"),
]


def load_slice(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as handle:
        return [line.strip() for line in handle if line.strip() and not line.startswith("#")]


failures = []


def fail(message):
    failures.append(message)
    print(f"FAIL {message}")


def ok(message):
    print(f"PASS {message}")


# 1. slice integrity
admitted = set()
for tree, slice_file in TREES:
    entries = load_slice(slice_file)
    duplicates = {e for e in entries if entries.count(e) > 1}
    missing = [e for e in entries if not os.path.isfile(os.path.join(ROOT, tree, e))]
    if duplicates:
        fail(f"{slice_file}: duplicate entries {sorted(duplicates)[:3]}")
    if missing:
        fail(f"{slice_file}: {len(missing)} missing paths e.g. {missing[:3]}")
    if not duplicates and not missing:
        ok(f"{slice_file}: {len(entries)} entries exist, no duplicates")
    admitted.update((tree, e) for e in entries)

# 2. dangling imports across all three trees
all_files = set()
for tree, _ in TREES:
    for dirpath, _, filenames in os.walk(os.path.join(ROOT, tree)):
        for name in filenames:
            if name.endswith(".java"):
                rel = os.path.relpath(os.path.join(dirpath, name), os.path.join(ROOT, tree))
                all_files.add((tree, rel))
excluded = all_files - admitted
excluded_fqn = {}
for tree, rel in excluded:
    pkg = os.path.dirname(rel).replace("/", ".")
    cls = os.path.basename(rel)[:-5]
    excluded_fqn[f"{pkg}.{cls}" if pkg else cls] = (tree, rel)

dangling = {}
for tree, rel in admitted:
    try:
        with open(os.path.join(ROOT, tree, rel), encoding="utf-8") as handle:
            text = handle.read()
    except OSError:
        continue
    for imp in re.findall(r"^import\s+(?:static\s+)?([\w.]+);", text, re.M):
        for cand in (imp, imp.rsplit(".", 1)[0]):
            if cand in excluded_fqn:
                dangling.setdefault(cand, set()).add(rel)
if dangling:
    for target, sources in sorted(dangling.items(), key=lambda kv: -len(kv[1]))[:10]:
        fail(f"dangling import {target} <- {sorted(sources)[:3]}")
else:
    ok(f"no dangling imports across {len(admitted)} admitted files ({len(excluded)} excluded)")

# 3. mixins
with open(os.path.join(ROOT, "fabric/src/main/resources/mekanism.mixins.json"), encoding="utf-8") as handle:
    mixins = json.load(handle)
package = mixins["package"].replace(".", "/")
platform = set(load_slice("fabric/platform-slice.txt"))
not_admitted = []
declared = 0
for group in ("mixins", "client", "server"):
    for name in mixins.get(group, []):
        declared += 1
        candidate = f'{package}/{name.replace(".", "/")}.java'
        if candidate not in platform:
            not_admitted.append(candidate)
if not_admitted:
    for candidate in not_admitted:
        fail(f"mixin declared but not admitted: {candidate}")
else:
    ok(f"all {declared} declared mixins are admitted")

# 4. entry points
with open(os.path.join(ROOT, "fabric/src/main/resources/fabric.mod.json"), encoding="utf-8") as handle:
    mod_json = json.load(handle)
missing_entrypoints = []
for kind, entries in mod_json.get("entrypoints", {}).items():
    for entry in entries:
        value = entry if isinstance(entry, str) else entry.get("value", entry.get("adapter", ""))
        if isinstance(value, str) and "." in value:
            cls = value.split("::")[0]
            if not os.path.isfile(os.path.join(ROOT, "fabric/src/main/java", cls.replace(".", "/") + ".java")):
                missing_entrypoints.append(f"{kind}:{value}")
if missing_entrypoints:
    for entry in missing_entrypoints:
        fail(f"entry point class missing: {entry}")
else:
    ok("all fabric.mod.json entry point classes exist")

print()
if failures:
    print(f"port slice audit: {len(failures)} check(s) failed")
    raise SystemExit(1)
print("port slice audit: all checks passed")
