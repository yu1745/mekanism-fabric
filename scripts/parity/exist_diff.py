#!/usr/bin/env python3
"""M-parity P1: diff the runtime existence probes. Neo is the oracle; Fabric results come per module (core/generators/additions/tools).
An id counts as 'exists' on a side when the block can be placed / the item can be given. Ids that fail on BOTH sides are
resource-only names (model variants, helper models) and are listed but not counted as gaps."""
import json, os, sys
E = os.path.join(os.path.dirname(__file__), "..", "..", "docs", "milestones", "evidence", "parity")
neo = json.load(open(os.path.join(E, "exist.neo.json")))
NS_MODULE = {"mekanism": "core", "mekanismgenerators": "generators", "mekanismadditions": "additions", "mekanismtools": "tools"}
fab = {}
for m in ("core", "generators", "additions", "tools"):
    d = json.load(open(os.path.join(E, f"exist.fab.{m}.json")))
    fab[m] = d
rows = {}
summary = []
for kind, ok in (("blocks", lambda v: v["registered"]), ("items", lambda v: v["given"])):
    for ns, mod in NS_MODULE.items():
        ids = sorted(i for i in neo[kind] if i.startswith(ns + ":"))
        both = [i for i in ids if ok(neo[kind][i]) and i in fab[mod][kind] and ok(fab[mod][kind][i])]
        only_neo = [i for i in ids if ok(neo[kind][i]) and not (i in fab[mod][kind] and ok(fab[mod][kind][i]))]
        only_fab = [i for i in ids if not ok(neo[kind][i]) and i in fab[mod][kind] and ok(fab[mod][kind][i])]
        neither = [i for i in ids if not ok(neo[kind][i]) and not (i in fab[mod][kind] and ok(fab[mod][kind][i]))]
        rows[f"{kind}/{ns}"] = {"both": len(both), "only_neo": only_neo, "only_fab": only_fab, "neither": len(neither)}
        summary.append((kind, ns, len(both), len(only_neo), len(only_fab), len(neither)))
# block-entity presence: a block that has a BE on one side but not the other
be_gap = []
for ns, mod in NS_MODULE.items():
    for b, v in neo["blocks"].items():
        if not b.startswith(ns + ":") or not v["registered"]:
            continue
        f = fab[mod]["blocks"].get(b)
        if f and f["registered"] and (v["be_keys"] is None) != (f["be_keys"] is None):
            be_gap.append({"block": b, "neo_has_be": v["be_keys"] is not None, "fab_has_be": f["be_keys"] is not None})
rows["block_entity_presence_gap"] = be_gap
json.dump(rows, open(os.path.join(E, "exist.diff.json"), "w"), indent=1, sort_keys=True)
print(f"{'kind':7} {'namespace':20} both only_neo only_fab neither")
for s in summary:
    print(f"{s[0]:7} {s[1]:20} {s[2]:4} {s[3]:8} {s[4]:8} {s[5]:7}")
for k, v in rows.items():
    if isinstance(v, dict) and (v["only_neo"] or v["only_fab"]):
        print(k, "only_neo:", v["only_neo"][:20], "only_fab:", v["only_fab"][:20])
print("BE presence gaps:", be_gap[:20])
