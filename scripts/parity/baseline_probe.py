#!/usr/bin/env python3
"""M-parity P3/P4 breadth: place every Mekanism block with a block entity on a stone floor and record its default block state,
the block-entity NBT right after placement and after 40 ticks idle. Usage: baseline_probe.py <side> <module> <out.json>"""
import json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from rpc import Rpc, RpcError
from exist_probe import static_ids  # noqa: F401  (id universe)

ORIGIN = [60, -58, 60]

def main():
    side, module, dest = sys.argv[1:4]
    blocks, _ = static_ids()
    blocks = [b for b in blocks if b.split(":")[0].startswith("mekanism")]
    r = Rpc(side, module)
    r.command(f"forceload add {ORIGIN[0]} {ORIGIN[2]}")
    r.command("gamerule doDaylightCycle false"); r.command("time set day"); r.command("weather clear")
    r.command(f"fill {ORIGIN[0]-2} {ORIGIN[1]-1} {ORIGIN[2]-2} {ORIGIN[0]+2} {ORIGIN[1]+3} {ORIGIN[2]+2} minecraft:air")
    r.command(f"fill {ORIGIN[0]-2} {ORIGIN[1]-1} {ORIGIN[2]-2} {ORIGIN[0]+2} {ORIGIN[1]-1} {ORIGIN[2]+2} minecraft:stone")
    out = {}
    for b in blocks:
        entry = {}
        try:
            r.call("world.setBlock", {"pos": ORIGIN, "block": b})
        except RpcError as e:
            out[b] = {"registered": False}; continue
        try:
            gb = r.call("world.getBlock", {"pos": ORIGIN})
            entry["state"] = gb["state"].get("props", {}); entry["has_be"] = gb.get("hasBlockEntity")
        except RpcError as e:
            entry["state"] = {"error": e.message[:60]}
        try:
            entry["nbt"] = r.call("be.getNbt", {"pos": ORIGIN})["nbt"]
            t0 = r.call("server.status")["tick"]
            r.call("wait.until", {"predicate": f"tick >= {t0 + 40}", "timeoutTicks": 300, "sprint": True})
            entry["nbt_after"] = r.call("be.getNbt", {"pos": ORIGIN})["nbt"]
        except RpcError:
            entry["nbt"] = None
        out[b] = entry
        r.call("world.setBlock", {"pos": ORIGIN, "block": "minecraft:air"})
    json.dump(out, open(dest, "w"), indent=1, sort_keys=True)
    print(f"{side}: {sum(1 for v in out.values() if v.get('registered', True))} blocks recorded, {sum(1 for v in out.values() if v.get('nbt'))} with block-entity NBT")

main()
