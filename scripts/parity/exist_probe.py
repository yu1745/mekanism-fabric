#!/usr/bin/env python3
"""M-parity P1: runtime existence probe. For every block/item id known from the static resource union (official source tree
assets + Fabric release jars) place the block / give the item on one side and record the outcome.
Usage: exist_probe.py <neo|fab> <module> <namespace,...> <out.json>
"""
import glob, json, os, re, sys, zipfile
sys.path.insert(0, os.path.dirname(__file__))
from rpc import Rpc, RpcError, ROOT

OFFICIAL = "/tmp/mekanism-neoforge/src"

def static_ids():
    blocks, items = set(), set()
    for root, _, files in os.walk(OFFICIAL):
        r = root.rstrip("/")
        if "/assets/" not in r:
            continue
        ns = r.split("/assets/")[1].split("/")[0]
        if r.endswith("/blockstates"):
            blocks.update(f"{ns}:{f[:-5]}" for f in files if f.endswith(".json"))
        elif r.endswith("models/item"):
            items.update(f"{ns}:{f[:-5]}" for f in files if f.endswith(".json"))
    for jar in glob.glob(os.path.join(ROOT, "dist", "*.jar")):
        for n in zipfile.ZipFile(jar).namelist():
            m = re.match(r"assets/([^/]+)/blockstates/(.+)\.json$", n)
            if m: blocks.add(f"{m[1]}:{m[2]}")
            m = re.match(r"assets/([^/]+)/models/item/(.+)\.json$", n)
            if m: items.add(f"{m[1]}:{m[2]}")
    return sorted(blocks), sorted(items)

def safe_clear(r):
    try:
        r.command("clear @p")
    except RpcError:
        pass  # nothing to clear

def main():
    side, module, nss, dest = sys.argv[1], sys.argv[2], sys.argv[3].split(","), sys.argv[4]
    blocks, items = static_ids()
    blocks = [b for b in blocks if b.split(":")[0] in nss]
    items = [i for i in items if i.split(":")[0] in nss]
    r = Rpc(side, module)
    pos = [0, -40, 0]
    out = {"side": side, "module": module, "namespaces": nss, "blocks": {}, "items": {}}
    for b in blocks:
        try:
            res = r.call("world.setBlock", {"pos": pos, "block": b})
            entry = {"registered": True}
            try:
                nbt = r.call("be.getNbt", {"pos": pos})["nbt"]
                entry["be_keys"] = sorted(nbt.keys())
            except RpcError:
                entry["be_keys"] = None
        except RpcError as e:
            entry = {"registered": False, "error": e.message[:80]}
        out["blocks"][b] = entry
    r.call("world.setBlock", {"pos": pos, "block": "minecraft:air"})
    safe_clear(r)
    for i in items:
        try:
            o = r.command(f"give @p {i} 1")
            out["items"][i] = {"given": o.get("success", False) and "Gave" in o.get("output", ""), "output": o.get("output", "")[:60]}
        except RpcError as e:
            out["items"][i] = {"given": False, "output": e.message[:80]}
    safe_clear(r)
    json.dump(out, open(dest, "w"), indent=1, sort_keys=True)
    nb = sum(1 for v in out["blocks"].values() if v["registered"]); ni = sum(1 for v in out["items"].values() if v["given"])
    print(f"{side}/{module}: blocks {nb}/{len(blocks)} items {ni}/{len(items)}")

if __name__ == "__main__":
    main()
