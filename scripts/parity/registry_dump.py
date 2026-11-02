#!/usr/bin/env python3
"""M-parity P1: dump runtime registry keys of one side (neo|fab) through mdb reflect.
Usage: registry_dump.py <neo|fab> <out.json>
"""
import json, subprocess, sys, os

MDB = ["java", "-jar", os.environ.get("MDB_JAR", os.path.expanduser("~/server-v2/develop/mcdebug-wt-121/mcdebug-cli.jar"))]
REGISTRIES = {  # BuiltInRegistries field -> label
    "BLOCK": "block", "ITEM": "item", "BLOCK_ENTITY_TYPE": "block_entity_type", "ENTITY_TYPE": "entity_type",
    "MENU": "menu", "FLUID": "fluid", "RECIPE_TYPE": "recipe_type", "RECIPE_SERIALIZER": "recipe_serializer",
    "SOUND_EVENT": "sound_event", "PARTICLE_TYPE": "particle_type", "CREATIVE_MODE_TAB": "creative_tab",
    "DATA_COMPONENT_TYPE": "data_component_type", "MOB_EFFECT": "mob_effect", "ATTRIBUTE": "attribute",
    "DAMAGE_TYPE": None,
}

def call(session, field):
    out = subprocess.run(MDB + ["--session", session, "reflect", "call", "--class",
                                "net.minecraft.core.registries.BuiltInRegistries", "--path", field, "--method", "keySet"],
                         capture_output=True, text=True, timeout=120)
    if out.returncode != 0:
        return None, out.stderr.strip()[:200]
    v = json.loads(out.stdout)["value"]
    return v, None

def main():
    side, dest = sys.argv[1], sys.argv[2]
    # keySet results above the mdb summary limit are cut off, so only registries that fit are dumped; blocks, items and
    # block entities are covered by exist_probe.py instead.
    ns_prefix = ("mekanism",)
    result = {}
    for field, label in REGISTRIES.items():
        if label is None:
            continue
        vals, err = call(side, field)
        if vals is None:
            result[label] = {"error": err}
            continue
        # reflect summarises very large collections: refuse silently-truncated data
        if isinstance(vals, list) and vals and isinstance(vals[-1], dict) and "$truncated" in vals[-1]:
            result[label] = {"error": f"truncated by {vals[-1]['$truncated']}"}
            continue
        result[label] = sorted(v for v in vals if v.split(":")[0].startswith(ns_prefix))
    json.dump(result, open(dest, "w"), indent=1, sort_keys=True)
    for k, v in result.items():
        print(side, k, len(v) if isinstance(v, list) else v)

main()
