"""Shared NBT flattening for the parity diffs."""
def flat(v, p=""):
    if isinstance(v, dict):
        for k in sorted(v):
            yield from flat(v[k], f"{p}.{k}" if p else k)
    elif isinstance(v, list):
        for i, x in enumerate(v):
            yield from flat(x, f"{p}[{i}]")
    else:
        yield p, v
