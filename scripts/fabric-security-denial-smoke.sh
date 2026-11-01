#!/usr/bin/env bash
# Two-player security check (W-120).
#
# Starts a throwaway dedicated server with one private machine, then connects two clients with different usernames.
# Both right-click the same machine through the client fixture; the owner must get the machine screen and the intruder
# must get none. The fixture logs SECURITY_FIXTURE_ALLOWED or SECURITY_FIXTURE_DENIED, and this script asserts one of
# each, so the check only passes when the server distinguishes the two players.
#
# Prerequisite: a remapped release jar (`./build.sh`). Requires Linux, systemd user scope and Xvfb for the clients.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
PROPERTIES="$ROOT/fabric/gradle.properties"
CACHE_DIR=${FABRIC_SMOKE_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/mekanism-fabric-smoke}
TIMEOUT=${FABRIC_SECURITY_SMOKE_TIMEOUT:-900}
KEEP_SERVER=${FABRIC_SMOKE_KEEP_SERVER:-0}
OWNER=${FABRIC_SECURITY_SMOKE_OWNER:-Alice}
INTRUDER=${FABRIC_SECURITY_SMOKE_INTRUDER:-Bob}
TARGET=${FABRIC_SECURITY_SMOKE_TARGET:-0,100,0}

SERVER_DIR=
LOG_FILE=
CLIENT_SCOPES=()

property() {
  local key=$1 value
  value=$(awk -F= -v key="$key" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$PROPERTIES")
  if [[ -z "$value" ]]; then
    echo "fabric-security-smoke: missing $key in $PROPERTIES" >&2
    exit 2
  fi
  printf '%s\n' "$value"
}

MINECRAFT_VERSION=$(property minecraft_version)
LOADER_VERSION=$(property loader_version)
FABRIC_API_VERSION=$(property fabric_api_version)
TEAM_REBORN_ENERGY_VERSION=$(property team_reborn_energy_version)
INSTALLER_VERSION=${FABRIC_INSTALLER_VERSION:-1.1.2}

cleanup() {
  local status=$?
  trap - EXIT INT TERM
  for scope in "${CLIENT_SCOPES[@]}"; do
    systemctl --user stop "$scope" >/dev/null 2>&1 || true
    systemctl --user reset-failed "$scope" >/dev/null 2>&1 || true
  done
  if [[ -n "$SERVER_DIR" && -d "$SERVER_DIR" ]]; then
    local pid_file="$SERVER_DIR/server.pid"
    if [[ -f "$pid_file" ]]; then
      local pid pgid
      pid=$(cat "$pid_file" 2>/dev/null || true)
      if [[ -n "$pid" ]]; then
        pgid=$(ps -o pgid= -p "$pid" 2>/dev/null | tr -d ' ')
        [[ -n "$pgid" ]] && kill -TERM -- "-$pgid" 2>/dev/null || true
        sleep 2
        [[ -n "$pgid" ]] && kill -KILL -- "-$pgid" 2>/dev/null || true
      fi
    fi
    if [[ "$KEEP_SERVER" == 1 ]]; then
      printf 'fabric-security-smoke: retained server: %s\n' "$SERVER_DIR" >&2
    else
      rm -rf "$SERVER_DIR"
    fi
  fi
  exit "$status"
}
trap cleanup EXIT INT TERM

fetch() {
  local url=$1 destination=$2 temporary="${2}.part.$$"
  if [[ -s "$destination" ]] && jar tf "$destination" >/dev/null 2>&1; then
    return
  fi
  rm -f "$temporary"
  printf 'fabric-security-smoke: downloading %s\n' "$url" >&2
  curl --fail --location --retry 3 --retry-all-errors --connect-timeout 15 --max-time 300 -o "$temporary" "$url"
  mv "$temporary" "$destination"
}

mkdir -p "$CACHE_DIR"
LAUNCHER_JAR="$CACHE_DIR/fabric-server-launch.jar"
FABRIC_API_JAR="$CACHE_DIR/fabric-api-${FABRIC_API_VERSION}.jar"
ENERGY_JAR="$CACHE_DIR/energy-${TEAM_REBORN_ENERGY_VERSION}.jar"
fetch "https://meta.fabricmc.net/v2/versions/loader/${MINECRAFT_VERSION}/${LOADER_VERSION}/${INSTALLER_VERSION}/server/jar" "$LAUNCHER_JAR"
fetch "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${FABRIC_API_VERSION}/fabric-api-${FABRIC_API_VERSION}.jar" "$FABRIC_API_JAR"
fetch "https://maven.fabricmc.net/teamreborn/energy/${TEAM_REBORN_ENERGY_VERSION}/energy-${TEAM_REBORN_ENERGY_VERSION}.jar" "$ENERGY_JAR"

MOD_JARS=()
while IFS= read -r jar; do MOD_JARS+=("$jar"); done < <(find "$ROOT/dist" -maxdepth 1 -name '*.jar' | sort)
if [[ ${#MOD_JARS[@]} -ne 4 ]]; then
  printf 'fabric-security-smoke: expected four release jars, found %d\n' "${#MOD_JARS[@]}" >&2
  exit 2
fi

# Offline UUIDs are what an offline-mode server assigns; computing them here lets the console assign machine ownership
# without a player having to place the block first.
offline_uuid() {
  python3 - "$1" <<'PY'
import hashlib, sys, uuid
name = sys.argv[1]
digest = bytearray(hashlib.md5(f"OfflinePlayer:{name}".encode()).digest())
digest[6] = (digest[6] & 0x0f) | 0x30
digest[8] = (digest[8] & 0x3f) | 0x80
print(uuid.UUID(bytes=bytes(digest)))
PY
}
OWNER_UUID=$(offline_uuid "$OWNER")
OWNER_UUID_INTS=$(python3 - "$OWNER_UUID" <<'PY'
import sys, uuid
#NBT stores a UUID as four signed 32-bit ints, most significant first.
u = uuid.UUID(sys.argv[1])
ints = [(u.int >> shift) & 0xFFFFFFFF for shift in (96, 64, 32, 0)]
print(",".join(str(i - (1 << 32) if i >= (1 << 31) else i) for i in ints))
PY
)

SERVER_DIR="$ROOT/fabric/run/security-server"
rm -rf "$SERVER_DIR"
mkdir -p "$SERVER_DIR"
LOG_FILE="$SERVER_DIR/console.log"
printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
PORT=$(python3 -c 'import socket;s=socket.socket();s.bind(("127.0.0.1",0));print(s.getsockname()[1]);s.close()')
printf 'online-mode=false\nserver-ip=127.0.0.1\nserver-port=%s\nspawn-protection=0\nmax-players=4\nview-distance=4\n' "$PORT" > "$SERVER_DIR/server.properties"

#The dev server's console belongs to its systemd scope, so the world is prepared from a datapack. Functions are parsed
#when the datapack loads, before mod blocks exist, so they only touch vanilla blocks: two command blocks carry the mod
#block id and the ownership merge as command strings, which are parsed when they execute instead.
DATAPACK="$SERVER_DIR/world/datapacks/security"
mkdir -p "$DATAPACK/data/security/function" "$DATAPACK/data/minecraft/tags/function"
cat > "$DATAPACK/pack.mcmeta" <<'EOF'
{"pack": {"pack_format": 48, "description": "security fixture setup"}}
EOF
cat > "$DATAPACK/data/minecraft/tags/function/tick.json" <<'EOF'
{"values": ["security:setup"]}
EOF
cat > "$DATAPACK/data/security/function/setup.mcfunction" <<'EOF'
scoreboard objectives add security dummy
scoreboard players add #t security 1
execute if score #t security matches 1 run setblock 0 100 5 minecraft:command_block{Command:"setblock 0 100 0 mekanism:enrichment_chamber",auto:1b}
execute if score #t security matches 5 run setblock 0 100 6 minecraft:command_block{Command:"data merge block 0 100 0 {component_security:{owner:[I;OWNER_INTS],security_mode:1b}}",auto:1b}
execute if score #t security matches 8 run setblock 0 100 7 minecraft:command_block{Command:"data get block 0 100 0",auto:1b}
execute if score #t security matches 12 run say SECURITY_SERVER_READY
execute if score #t security matches 20 run setblock 0 100 8 minecraft:command_block{Command:"data get block 0 100 0",auto:1b}
EOF
#The owner UUID is substituted outside the heredoc so the command string stays plain vanilla text otherwise.
sed -i "s/OWNER_INTS/${OWNER_UUID_INTS}/" "$DATAPACK/data/security/function/setup.mcfunction"

SECURITY_SCOPE="mekanism-security-server-$$"
CLIENT_SCOPES+=("$SECURITY_SCOPE")
systemd-run --user --scope --collect --quiet --unit="$SECURITY_SCOPE" \
  "$ROOT/gradlew" -p "$ROOT/fabric" --no-daemon --console=plain \
  -Dmekanism.security.serverDir="run/security-server" \
  -Dmekanism.security.serverPort="$PORT" \
  runSecurityDenialServer > "$LOG_FILE" 2>&1 &

for _ in $(seq 1 180); do
  grep -q 'Done (' "$LOG_FILE" 2>/dev/null && break
  sleep 1
done
if ! grep -q 'Done (' "$LOG_FILE" 2>/dev/null; then
  echo "fabric-security-smoke: server never became ready" >&2
  tail -20 "$LOG_FILE" >&2
  exit 1
fi
sleep 2

run_client() {
  local number=$1 username=$2 expect=$3
  local relative="run/security-$number"
  local dir="$ROOT/fabric/$relative"
  local console_log="$dir/console.log"
  local scope="mekanism-security-client-$number-$$"
  rm -rf "$dir"
  mkdir -p "$dir"
  printf 'onboardAccessibility:false\n' > "$dir/options.txt"
  CLIENT_SCOPES+=("$scope")
  printf 'fabric-security-smoke: client %d (%s, expect %s)\n' "$number" "$username" "$expect"
  systemd-run --user --scope --collect --quiet --unit="$scope" \
    xvfb-run -a "$ROOT/gradlew" -p "$ROOT/fabric" --no-daemon --console=plain \
    -Dmekanism.security.clientDir="$relative" \
    -Dmekanism.security.address="127.0.0.1:$PORT" \
    -Dmekanism.security.username="$username" \
    -Dmekanism.security.target="$TARGET" \
    -Dmekanism.security.expect="$expect" \
    runSecurityDenialClient > "$console_log" 2>&1 &
  local deadline=$((SECONDS + TIMEOUT / 2))
  while (( SECONDS < deadline )); do
    if grep -qE 'SECURITY_FIXTURE_(ALLOWED|DENIED|UNEXPECTED)' "$console_log" 2>/dev/null; then
      break
    fi
    sleep 5
  done
  systemctl --user stop "$scope" >/dev/null 2>&1 || true
  systemctl --user reset-failed "$scope" >/dev/null 2>&1 || true
  sleep 5
}

run_client 1 "$OWNER" allow
run_client 2 "$INTRUDER" deny

touch "$SERVER_DIR/clients-done"

failures=0
check() {
  local description=$1 pattern=$2 file=$3
  if grep -Eq "$pattern" "$file" 2>/dev/null; then
    printf 'PASS %s\n' "$description"
  else
    printf 'FAIL %s\n' "$description"
    failures=$((failures + 1))
  fi
}

check "dedicated server reached Done" 'Done \([0-9.]+s\)!' "$LOG_FILE"
check "machine ownership was written to the block" "SECURITY_SERVER_READY" "$LOG_FILE"
check "owner got the machine screen" 'SECURITY_FIXTURE_ALLOWED' "$ROOT/fabric/run/security-1/console.log"
check "intruder got no machine screen" 'SECURITY_FIXTURE_DENIED' "$ROOT/fabric/run/security-2/console.log"

if (( failures > 0 )); then
  echo "fabric-security-smoke: FAILED ($failures checks)" >&2
  exit 1
fi
echo "fabric-security-smoke: PASSED"
