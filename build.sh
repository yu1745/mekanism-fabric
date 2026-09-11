#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════
# build.sh — Mekanism Fabric 统一构建契约
#
# 契约（所有 T0/T1 仓库一致，外部调用方式不得更改）：
#   用法:   ./build.sh [--clean] [--out <dir>]      # 默认 out=dist
#   退出码: 0=成功；非 0=失败（原因写 stderr）
#   产出:   <out>/*.jar  +  <out>/build-info.json
#           build-info.json = { repo, version, source_commit, jars:[{file,sha256}], built_at }
#
# 当前只发布 fabric/ 独立 Loom 构建的 remapped JAR；根 NeoForge 构建仅存在于
# 基线提交中，不再作为当前移植源码的构建入口。
# 可选：build-jars.txt 存在则只保留其中列出的 jar 文件名模式。
# ═══════════════════════════════════════════════════════════════════════
set -euo pipefail
cd "$(dirname "$0")"

OUT=dist
CLEAN=0
while [ $# -gt 0 ]; do
  case "$1" in
    --clean) CLEAN=1 ;;
    --out) shift; OUT="$1" ;;
    *) echo "usage: $0 [--clean] [--out <dir>]" >&2; exit 2 ;;
  esac
  shift
done

if [ ! -x ./gradlew ]; then
  echo "build.sh: 本仓库无 ./gradlew，请改写本脚本适配（或检查目录）" >&2
  exit 1
fi

if [ "$CLEAN" = 1 ]; then
  ./gradlew -p fabric clean --console=plain
fi
./fabric-build.sh

rm -rf "$OUT"
mkdir -p "$OUT"

# 只收集 Fabric Loom 的 remapped 发布 JAR。
find fabric/build/libs -maxdepth 1 -type f -name '*.jar' \
  ! -name '*-sources.jar' ! -name '*-dev.jar' -print0 \
  | while IFS= read -r -d '' j; do cp "$j" "$OUT/"; done

# 可选过滤：build-jars.txt 每行一个 glob，只保留匹配的 jar
if [ -f build-jars.txt ]; then
  python3 - "$OUT" <<'PYEOF'
import sys, glob, pathlib
out = pathlib.Path(sys.argv[1])
keep = [p.strip() for p in pathlib.Path("build-jars.txt").read_text().splitlines() if p.strip()]
for j in list(out.glob("*.jar")):
    if not any(j.match(k) for k in keep):
        j.unlink()
PYEOF
fi

if ! ls "$OUT"/*.jar >/dev/null 2>&1; then
  echo "build.sh: 构建完成但未收集到任何产物 jar（检查 build 输出或 build-jars.txt 过滤）" >&2
  exit 1
fi

python3 - "$OUT" <<'PYEOF'
import hashlib, json, subprocess, sys, datetime, pathlib
out = pathlib.Path(sys.argv[1])

def sha256(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()

def git_head():
    r = subprocess.run(["git", "rev-parse", "HEAD"], capture_output=True, text=True)
    return r.stdout.strip() if r.returncode == 0 else None

def git_remote():
    r = subprocess.run(["git", "remote", "get-url", "origin"], capture_output=True, text=True)
    return r.stdout.strip() if r.returncode == 0 else None

# version：优先 Fabric 构建配置，再回退根配置或 git describe
version = None
for gp in (pathlib.Path("fabric/gradle.properties"), pathlib.Path("gradle.properties")):
    if not gp.exists():
        continue
    for line in gp.read_text().splitlines():
        line = line.strip()
        if line.startswith("mod_version=") or line.startswith("version="):
            version = line.split("=", 1)[1].strip()
            break
    if version:
        break
if not version:
    r = subprocess.run(["git", "describe", "--tags", "--always"], capture_output=True, text=True)
    version = r.stdout.strip() or "unknown"

jars = [{"file": j.name, "sha256": sha256(j)} for j in sorted(out.glob("*.jar"))]
info = {
    "repo": git_remote(),
    "version": version,
    "source_commit": git_head(),
    "jars": jars,
    "built_at": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
}
(out / "build-info.json").write_text(json.dumps(info, ensure_ascii=False, indent=2) + "\n")
print(f"build.sh: {len(jars)} jar(s) -> {out}/  version={version} commit={info['source_commit']}")
PYEOF
