#!/usr/bin/env python3
"""M-parity P3/P4: capacity sweep. For every Mekanism block that has a block entity, over-fill energy containers, fluid tanks and
chemical tanks (indices 0..3) through `data merge block`, tick, and read back: the loader clamps to each container's real capacity,
so the clamped numbers are the configured capacities. Also records which indices exist.  Usage: capacity_probe.py <side> <module> <out.json>"""
import json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from rpc import Rpc, RpcError

ORIGIN = [60, -58, 70]
OVER = ("{energy_containers:[" + ",".join(f"{{container:{i}b,stored:999999999999999L}}" for i in range(4)) + "],"
        "fluid_tanks:[" + ",".join(f"{{tank:{i}b,stored:{{id:\"minecraft:water\",amount:2000000000}}}}" for i in range(4)) + "],"
        "chemical_tanks:[" + ",".join(f"{{tank:{i}b,stored:{{id:\"mekanism:hydrogen\",amount:2000000000L}}}}" for i in range(4)) + "]}")

def main():
    side, module, dest = sys.argv[1:4]
    base = json.load(open(os.path.join(os.path.dirname(__file__), "..", "..", "docs", "milestones", "evidence", "parity", "base.neo.json")))
    blocks = sorted(b for b, v in base.items() if v.get("nbt") is not None)
    r = Rpc(side, module)
    r.command(f"forceload add {ORIGIN[0]} {ORIGIN[2]}")
    r.command(f"fill {ORIGIN[0]-2} {ORIGIN[1]-1} {ORIGIN[2]-2} {ORIGIN[0]+2} {ORIGIN[1]+3} {ORIGIN[2]+2} minecraft:air")
    r.command(f"fill {ORIGIN[0]-2} {ORIGIN[1]-1} {ORIGIN[2]-2} {ORIGIN[0]+2} {ORIGIN[1]-1} {ORIGIN[2]+2} minecraft:stone")
    out = {}
    for b in blocks:
        try:
            r.call("world.setBlock", {"pos": ORIGIN, "block": b})
            for cmd in (f"data merge block {ORIGIN[0]} {ORIGIN[1]} {ORIGIN[2]} {OVER}",):
                try:
                    r.command(cmd, tolerate=("Nothing changed", "That position", "No blocks", "already"))
                except RpcError as e:
                    out[b] = {"merge_error": e.message[:80]}
            t0 = r.call("server.status")["tick"]
            r.call("wait.until", {"predicate": f"tick >= {t0 + 3}", "timeoutTicks": 100, "sprint": True})
            nbt = r.call("be.getNbt", {"pos": ORIGIN})["nbt"]
            ent = out.setdefault(b, {})
            ent["energy"] = {c["container"]: c["stored"] for c in nbt.get("energy_containers", [])}
            ent["fluid"] = {t["tank"]: [t["stored"]["id"], t["stored"]["amount"]] for t in nbt.get("fluid_tanks", [])}
            ent["chemical"] = {t["tank"]: [t["stored"]["id"], t["stored"]["amount"]] for t in nbt.get("chemical_tanks", [])}
        except RpcError as e:
            out[b] = {"error": e.message[:80]}
        r.call("world.setBlock", {"pos": ORIGIN, "block": "minecraft:air"})
    json.dump(out, open(dest, "w"), indent=1, sort_keys=True)
    n = sum(1 for v in out.values() if v.get("energy") or v.get("fluid") or v.get("chemical"))
    print(f"{side}: {len(out)} block entities swept, {n} with at least one energy/fluid/chemical container")

main()
