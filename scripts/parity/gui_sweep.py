#!/usr/bin/env python3
"""M-parity P5: open the GUI of every Mekanism block entity block that has one and record screen class, title, slot count, widget count,
slot geometry and any render problems. Needs a client session (neo|fab) in a world with cheats. Usage: gui_sweep.py <side> <module> <out.json>"""
import json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from rpc import Rpc, RpcError

O = [80, -60, 80]

def main():
    side, module, dest = sys.argv[1:4]
    base = json.load(open(os.path.join(os.path.dirname(__file__), "..", "..", "docs", "milestones", "evidence", "parity", "base.neo.json")))
    blocks = sorted(b for b, v in base.items() if v.get("nbt") is not None)
    s, c = Rpc(side, module), Rpc(side, module, "client")
    s.command(f"forceload add {O[0]} {O[2]}")
    out = {}
    for b in blocks:
        entry = {}
        try:
            s.call("world.setBlock", {"pos": O, "block": b})
        except RpcError:
            out[b] = {"registered": False}; continue
        s.command(f"tp @p {O[0] + 0.5} {O[1]} {O[2] - 2.5}")
        try:
            c.call("gui.settle")
            res = c.call("gui.openBlock", {"x": O[0], "y": O[1], "z": O[2]})
            c.call("gui.settle")
            if res.get("ok") and res.get("screen"):
                scr = c.call("gui.screen", {})
                entry = {"opened": True, "class": res["screen"], "title": res.get("screenTitle"), "slots": res.get("slotCount"),
                         "gui": scr.get("gui"), "widgets": len(scr.get("widgets", [])),
                         "slot_geometry": sorted((sl["index"], sl["guiX"], sl["guiY"]) for sl in scr.get("slots", []))}
                try:
                    pr = c.call("gui.problems", {})
                    entry["problems"] = len(pr.get("problems", pr.get("entries", [])))
                except RpcError:
                    pass
                c.call("gui.close")
            else:
                entry = {"opened": False, "reason": res.get("reason")}
        except RpcError as e:
            entry = {"opened": False, "error": e.message[:80]}
        out[b] = entry
        s.call("world.setBlock", {"pos": O, "block": "minecraft:air"})
        try: c.call("gui.close")
        except RpcError: pass
    json.dump(out, open(dest, "w"), indent=1, sort_keys=True)
    print(f"{side}/{module}: {sum(1 for v in out.values() if v.get('opened'))} GUIs opened of {len(out)} block entities")

main()
