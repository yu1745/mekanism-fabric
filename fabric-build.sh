#!/usr/bin/env bash
# Independent Fabric bootstrap build. The root build.sh remains the NeoForge oracle.
set -euo pipefail
cd "$(dirname "$0")"
exec ./gradlew -p fabric build --console=plain "$@"
