#!/usr/bin/env python3
"""M-parity: diff two probe outputs (oracle first). Usage: probe_diff.py <oracle.json> <candidate.json> [out.json]
Flattens each case's nbt_after to path->value and reports paths that differ, with relative error for numbers."""
import json, os, sys
sys.path.insert(0, os.path.dirname(__file__))

from probe_diff_lib import flat

def main():
    a = json.load(open(sys.argv[1])); b = json.load(open(sys.argv[2]))
    bi = {r["id"]: r for r in b["results"]}
    report, bad = [], 0
    for ra in a["results"]:
        rb = bi.get(ra["id"])
        row = {"id": ra["id"], "status": "match", "diffs": []}
        if rb is None:
            row["status"] = "missing"; bad += 1
        elif "error" in ra or "error" in rb:
            row["status"] = "error"; row["diffs"].append({"oracle": ra.get("error"), "candidate": rb.get("error")}); bad += 1
        else:
            fa, fb = dict(flat(ra["nbt_after"])), dict(flat(rb["nbt_after"]))
            for k in sorted(set(fa) | set(fb)):
                if fa.get(k) != fb.get(k):
                    d = {"path": k, "oracle": fa.get(k), "candidate": fb.get(k)}
                    if isinstance(fa.get(k), (int, float)) and isinstance(fb.get(k), (int, float)) and fa[k]:
                        d["rel_err"] = round((fb[k] - fa[k]) / fa[k], 5)
                    row["diffs"].append(d)
            if row["diffs"]:
                row["status"] = "diff"; bad += 1
        report.append(row)
    for r in report:
        print(f"{r['status']:8} {r['id']}")
        for d in r["diffs"]:
            print("           ", d)
    print(f"{len(report) - bad}/{len(report)} cases match")
    if len(sys.argv) > 3:
        json.dump(report, open(sys.argv[3], "w"), indent=1)

main()
