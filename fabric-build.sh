#!/usr/bin/env bash
# Independent Fabric development build; root build.sh wraps this for release artifacts.
set -euo pipefail
cd "$(dirname "$0")"
exec ./gradlew -p fabric build --console=plain "$@"
