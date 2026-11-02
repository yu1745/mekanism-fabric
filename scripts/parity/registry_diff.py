#!/usr/bin/env python3
"""M-parity P1: per-namespace diff of the small registries (neo oracle vs the matching Fabric module session)."""
import json, os
E = os.path.join(os.path.dirname(__file__), "..", "..", "docs", "milestones", "evidence", "parity")
neo = json.load(open(os.path.join(E, "registry.neo.json")))
fab = {m: json.load(open(os.path.join(E, f"registry.fab.{m}.json"))) for m in ("core", "generators", "additions", "tools")}
ns_mod = {"mekanism": "core", "mekanismgenerators": "generators", "mekanismadditions": "additions", "mekanismtools": "tools"}
res = {}
for reg, v in neo.items():
    if not isinstance(v, list):
        continue
    for ns, m in ns_mod.items():
        n = {x for x in v if x.split(":")[0] == ns}
        fv = fab[m].get(reg)
        f = {x for x in fv if x.split(":")[0] == ns} if isinstance(fv, list) else set()
        res[f"{reg}/{ns}"] = {"neo": len(n), "fab": len(f), "only_neo": sorted(n - f), "only_fab": sorted(f - n)}
json.dump(res, open(os.path.join(E, "registry.diff.json"), "w"), indent=1, sort_keys=True)
print(f"{'registry/namespace':44} {'neo':>4} {'fab':>4}  gaps")
for k, v in sorted(res.items()):
    if v["neo"] or v["fab"]:
        print(f"{k:44} {v['neo']:4} {v['fab']:4}  {'OK' if not (v['only_neo'] or v['only_fab']) else 'neo-only %d, fab-only %d' % (len(v['only_neo']), len(v['only_fab']))}")
for k, v in sorted(res.items()):
    if v["only_neo"] or v["only_fab"]:
        print(k, "only_neo", v["only_neo"], "only_fab", v["only_fab"])
print("not dumped (mdb summary limit; covered by exist_probe):", [k for k, v in neo.items() if not isinstance(v, list)])
