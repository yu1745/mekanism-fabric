#!/bin/bash
# Sync the fabric build.gradle slice-count gate with the actual slice file counts.
set -e
cd "$(dirname "$0")/.."
API=$(grep -v '^#' fabric/api-slice.txt | grep -v '^$' | wc -l)
COMMON=$(grep -v '^#' fabric/common-slice.txt | grep -v '^$' | wc -l)
PLATFORM=$(grep -v '^#' fabric/platform-slice.txt | grep -v '^$' | wc -l)
sed -i "s/apiSlice.size() != [0-9]* || commonSlice.size() != [0-9]* || platformSlice.size() != [0-9]*/apiSlice.size() != $API || commonSlice.size() != $COMMON || platformSlice.size() != $PLATFORM/" fabric/build.gradle
echo "gate synced: api=$API common=$COMMON platform=$PLATFORM"
