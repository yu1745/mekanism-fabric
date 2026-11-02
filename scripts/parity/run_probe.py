#!/usr/bin/env python3
"""M-parity: table-driven functional probe runner.
Usage: run_probe.py <neo|fab> <module> <table.json> <out.json>

A table is {"cases": [ {id, block, [nbt], [commands], [setup], ticks, read[]} ]}. Per case the runner clears a platform,
places the block, applies setup, advances `ticks` game ticks (vanilla tick sprint through wait.until) and records:
  * be_nbt_before / be_nbt_after (full block-entity NBT; compare scripts choose the fields),
  * `read` extras: storage reads {kind: "storage", ...} or block-state reads.
setup steps: {"op":"nbt","data":{...}} (be.setNbt), {"op":"cmd","command":"... @X @Y @Z (origin coordinates) ..."}, {"op":"inv","slot":N,"item":"id","count":N},
{"op":"block","pos":[dx,dy,dz],"block":"id"} (extra blocks relative to the case origin), {"op":"storage_insert",...}.
Everything is relative to a per-case origin so cases cannot interfere.
"""
import json, os, sys, time
sys.path.insert(0, os.path.dirname(__file__))
from rpc import Rpc, RpcError

BASE = [20, -58, 20]  # superflat ground is y=-61 (grass); build at y=-58 above it, forceloaded around BASE

def add(p, d): return [p[0] + d[0], p[1] + d[1], p[2] + d[2]]

def advance(r, ticks):
    t0 = r.call("server.status")["tick"]
    r.call("wait.until", {"predicate": f"tick >= {t0 + ticks}", "timeoutTicks": ticks + 200, "sprint": True})
    return r.call("server.status")["tick"] - t0

def read_nbt(r, pos):
    try:
        return r.call("be.getNbt", {"pos": pos})["nbt"]
    except RpcError as e:
        return {"__error__": e.message[:100]}

def run_case(r, case, idx):
    origin = add(BASE, [idx * 8, 0, 0])
    r.command(f"forceload add {origin[0]} {origin[2]}")
    # clear a 7x5x7 volume and lay a stone floor
    for cmd in (f"fill {origin[0]-2} {origin[1]-1} {origin[2]-2} {origin[0]+4} {origin[1]+4} {origin[2]+4} minecraft:air",
                f"fill {origin[0]-2} {origin[1]-1} {origin[2]-2} {origin[0]+4} {origin[1]-1} {origin[2]+4} minecraft:stone"):
        r.command(cmd)
    for cmd in case.get("pre_commands", []):
        r.command(cmd)
    res = {"id": case["id"], "block": case["block"], "origin": origin}
    try:
        state = case.get("state")
        params = {"pos": origin, "block": case["block"]}
        if state: params["stateProps"] = state
        r.call("world.setBlock", params)
        for step in case.get("setup", []):
            op = step["op"]
            if op == "nbt":
                r.call("be.setNbt", {"pos": add(origin, step.get("pos", [0, 0, 0])), "nbt": step["data"]})
            elif op == "cmd":
                r.command(step["command"].replace("@X", str(origin[0])).replace("@Y", str(origin[1])).replace("@Z", str(origin[2])))
            elif op == "inv":
                r.call("inv.setSlot", {"pos": add(origin, step.get("pos", [0, 0, 0])), "slot": step["slot"],
                                       "item": step["item"], "count": step.get("count", 1)})
            elif op == "block":
                p = {"pos": add(origin, step["pos"]), "block": step["block"]}
                if step.get("state"): p["stateProps"] = step["state"]
                r.call("world.setBlock", p)
            elif op == "storage_insert":
                r.call("storage.insert", dict(step["params"], target={"kind": "block", "pos": add(origin, step.get("pos", [0, 0, 0]))}))
        res["nbt_before"] = read_nbt(r, origin)
        res["ran_ticks"] = advance(r, case["ticks"]) if case.get("ticks") else 0
        res["nbt_after"] = read_nbt(r, origin)
        for extra in case.get("extra_reads", []):
            res.setdefault("extra", {})[extra["name"]] = read_nbt(r, add(origin, extra["pos"]))
    except RpcError as e:
        res["error"] = e.message[:200]
    # clean up the whole volume so later cases start clean
    r.command(f"fill {origin[0]-2} {origin[1]} {origin[2]-2} {origin[0]+4} {origin[1]+4} {origin[2]+4} minecraft:air")
    return res

def main():
    side, module, table, dest = sys.argv[1:5]
    cases = json.load(open(table))["cases"]
    r = Rpc(side, module)
    r.command("time set day"); r.command("weather clear"); r.command("gamerule doDaylightCycle false"); r.command("gamerule doWeatherCycle false")
    out = {"side": side, "module": module, "table": os.path.basename(table), "results": []}
    for i, c in enumerate(cases):
        res = run_case(r, c, i)
        out["results"].append(res)
        print(f"{side} {c['id']}: " + ("ERROR " + res["error"] if "error" in res else "ok"))
    json.dump(out, open(dest, "w"), indent=1, sort_keys=True)

main()
