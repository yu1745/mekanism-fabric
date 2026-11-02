#!/usr/bin/env python3
"""M-parity: diff baseline probes (oracle neo vs candidate). Usage: base_diff.py <oracle.json> <candidate.json> [out.json]"""
import json, sys
sys.path.insert(0, __import__("os").path.dirname(__file__))
from probe_diff_lib import flat

a = json.load(open(sys.argv[1])); b = json.load(open(sys.argv[2]))
rows, nbad = {}, 0
for blk in sorted(a):
    x, y = a[blk], b.get(blk)
    d = []
    if y is None:
        d.append({"what": "missing in candidate"})
    else:
        if x.get("state") != y.get("state"):
            d.append({"what": "default state", "oracle": x.get("state"), "candidate": y.get("state")})
        if x.get("has_be") != y.get("has_be"):
            d.append({"what": "has_be", "oracle": x.get("has_be"), "candidate": y.get("has_be")})
        for fld in ("nbt", "nbt_after"):
            fa, fb = dict(flat(x.get(fld) or {})), dict(flat(y.get(fld) or {}))
            for k in sorted(set(fa) | set(fb)):
                if fa.get(k) != fb.get(k):
                    d.append({"what": fld, "path": k, "oracle": fa.get(k), "candidate": fb.get(k)})
    rows[blk] = d
    nbad += bool(d)
print(f"{len(rows) - nbad}/{len(rows)} blocks identical (default state + NBT at placement + NBT after 40 idle ticks)")
for blk, d in rows.items():
    if d:
        print(blk)
        for e in d[:6]:
            print("   ", e)
        if len(d) > 6:
            print(f"    ... {len(d) - 6} more")
if len(sys.argv) > 3:
    json.dump({k: v for k, v in rows.items() if v}, open(sys.argv[3], "w"), indent=1)
