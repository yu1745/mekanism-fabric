#!/usr/bin/env python3
"""M-parity P5: official world -> Fabric load. `place` (on the official side) lays out every Mekanism block that has a block entity on a
grid, seeds some state, waits, snapshots the block entity NBT to JSON and saves. `verify` (on any side that loaded the copied world) reads
every position back and diffs against the snapshot.
Usage: world_roundtrip.py place <side> <snapshot.json> | verify <side> <snapshot.json> <report.json>"""
import json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from rpc import Rpc, RpcError

X0, Y, Z0, STEP, COLS = 200, -60, 200, 3, 16
SEED = ('{energy_containers:[{container:0b,stored:5000L}],'
        'fluid_tanks:[{tank:0b,stored:{id:"minecraft:water",amount:1000}}],'
        'chemical_tanks:[{tank:0b,stored:{id:"mekanism:hydrogen",amount:1000L}}],'
        'items:[{slot:0b,item:{id:"minecraft:cobblestone",count:3}}]}')

def positions(blocks):
    return {b: [X0 + (i % COLS) * STEP, Y, Z0 + (i // COLS) * STEP] for i, b in enumerate(blocks)}

def main():
    mode, side = sys.argv[1], sys.argv[2]
    base = json.load(open(os.path.join(os.path.dirname(__file__), "..", "..", "docs", "milestones", "evidence", "parity", "base.neo.json")))
    blocks = sorted(b for b, v in base.items() if v.get("nbt") is not None)
    pos = positions(blocks)
    r = Rpc(side, "all")
    rows = (len(blocks) + COLS - 1) // COLS
    for cx in range(X0 // 16, (X0 + COLS * STEP) // 16 + 1):
        for cz in range(Z0 // 16, (Z0 + rows * STEP) // 16 + 1):
            r.command(f"forceload add {cx * 16} {cz * 16}")
    if mode == "place":
        r.command(f"fill {X0-2} {Y-1} {Z0-2} {X0 + COLS * STEP + 2} {Y + 3} {Z0 + rows * STEP + 2} minecraft:air")
        for b in blocks:
            p = pos[b]
            r.call("world.setBlock", {"pos": p, "block": b})
            try:
                r.command(f"data merge block {p[0]} {p[1]} {p[2]} {SEED}", tolerate=("Nothing changed", "already"))
            except RpcError:
                pass
        t0 = r.call("server.status")["tick"]
        r.call("wait.until", {"predicate": f"tick >= {t0 + 60}", "timeoutTicks": 400, "sprint": True})
        snap = {}
        for b in blocks:
            try:
                snap[b] = {"pos": pos[b], "state": r.call("world.getBlock", {"pos": pos[b]})["state"], "nbt": r.call("be.getNbt", {"pos": pos[b]})["nbt"]}
            except RpcError as e:
                snap[b] = {"pos": pos[b], "error": e.message[:80]}
        json.dump(snap, open(sys.argv[3], "w"), indent=1, sort_keys=True)
        print(f"{side}: placed and snapshotted {len(snap)} blocks")
        try:
            r.command("save-all flush")
        except RpcError:
            pass  # the owning session saves on stop
    else:
        snap = json.load(open(sys.argv[3]))
        t0 = r.call("server.status")["tick"]
        r.call("wait.until", {"predicate": f"tick >= {t0 + 20}", "timeoutTicks": 400, "sprint": True})
        report, bad = {}, 0
        sys.path.insert(0, os.path.dirname(__file__))
        from probe_diff_lib import flat
        for b, s in snap.items():
            if "error" in s:
                continue
            d = []
            try:
                st = r.call("world.getBlock", {"pos": s["pos"]})["state"]
                nbt = r.call("be.getNbt", {"pos": s["pos"]})["nbt"]
            except RpcError as e:
                report[b] = [{"what": "read failed", "error": e.message[:80]}]; bad += 1; continue
            if st != s["state"]:
                d.append({"what": "state", "saved": s["state"], "loaded": st})
            fa, fb = dict(flat(s["nbt"])), dict(flat(nbt))
            for k in sorted(set(fa) | set(fb)):
                if fa.get(k) != fb.get(k):
                    d.append({"what": "nbt", "path": k, "saved": fa.get(k), "loaded": fb.get(k)})
            if d:
                report[b] = d; bad += 1
        json.dump(report, open(sys.argv[4], "w"), indent=1)
        print(f"{side}: {len(snap) - bad}/{len(snap)} blocks identical after loading the copied world")
        for b, d in list(report.items())[:12]:
            print(" ", b, d[:3])

main()
