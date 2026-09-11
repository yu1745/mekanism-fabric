#!/usr/bin/env python3
"""Deterministically inventory NeoForge imports in src/**/*.java.

The output is intentionally plain text so PORT_MATRIX.md can be refreshed and
reviewed without requiring Gradle, a JVM, or third-party Python packages.
"""

from __future__ import annotations

import argparse
import collections
import re
from pathlib import Path

IMPORT_RE = re.compile(r"^\s*import\s+(net\.neoforged(?:\.[\w$]+)+)\s*;", re.MULTILINE)

# Ordered: an import is assigned to the first matching category.
CATEGORY_PATTERNS = (
    ("lifecycle", (r"\.fml\.common\.", r"\.fml\.event\.lifecycle\.", r"\.fml\.loading\.",
                   r"\.fml\.ModContainer$", r"ServerLifecycleHooks", r"\.distmarker\.")),
    ("registry", (r"\.registries\.", r"\.registries$")),
    ("config", (r"ModConfig", r"IConfigSpec", r"\.config\.")),
    ("network", (r"\.network\.", r"PacketDistributor")),
    ("capability+attachments", (r"\.capabilities\.", r"\.attachment")),
    ("item", (r"\.items\.", r"ItemAbilit", r"IClientItemExtensions")),
    ("fluid", (r"\.fluids\.", r"FluidStack", r"FluidType")),
    ("chemical", (r"\.chemical",)),  # Expected to be zero: chemicals are Mekanism-owned.
    ("energy", (r"\.energy\.",)),
    ("datagen", (r"\.data\.", r"GatherDataEvent", r"ExistingFileHelper")),
    ("integration", (r"\.compat\.", r"ModList", r"InterMod", r"testframework")),
    ("events", (r"\.event\.", r"\.bus\.api\.")),
    ("client", (r"\.client\.", r"\.api\.distmarker\.")),
)


def category_for(name: str) -> str:
    for category, patterns in CATEGORY_PATTERNS:
        if any(re.search(pattern, name, re.IGNORECASE) for pattern in patterns):
            return category
    return "other"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, help="repository root (default: parent of scripts/)")
    parser.add_argument("--top", type=int, default=20, help="number of frequent imports to print")
    args = parser.parse_args()

    root = (args.root or Path(__file__).resolve().parents[1]).resolve()
    source_root = root / "src"
    java_files = sorted(source_root.glob("**/*.java"), key=lambda path: path.as_posix())
    imports: collections.Counter[str] = collections.Counter()
    category_imports: collections.Counter[str] = collections.Counter()
    category_files: dict[str, set[str]] = collections.defaultdict(set)
    boundary_files: set[str] = set()

    for path in java_files:
        relative = path.relative_to(root).as_posix()
        names = IMPORT_RE.findall(path.read_text(encoding="utf-8"))
        if names:
            boundary_files.add(relative)
        for name in names:
            imports[name] += 1
            category = category_for(name)
            category_imports[category] += 1
            category_files[category].add(relative)

    print("source: src/**/*.java")
    print(f"java files: {len(java_files)}")
    print(f"NeoForge boundary files: {len(boundary_files)}")
    print(f"NeoForge imports: {sum(imports.values())}")
    print("\ncategory                    imports  files")
    print("--------------------------  -------  -----")
    categories = [name for name, _ in CATEGORY_PATTERNS] + ["other"]
    for category in categories:
        print(f"{category:26}  {category_imports[category]:7}  {len(category_files[category]):5}")

    print(f"\ntop {max(args.top, 0)} imports")
    print("-----  ------")
    for name, count in sorted(imports.items(), key=lambda item: (-item[1], item[0]))[: max(args.top, 0)]:
        print(f"{count:5}  {name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
