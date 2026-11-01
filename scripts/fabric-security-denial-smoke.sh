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
u = uuid.UUID(sys.argv[1])
msb, lsb = u.int >> 64, u.int & ((1 << 64) - 1)
parts = []
for v in (msb, lsb):
    parts.append(str((v >> 32) - (1 << 32) if v >> 31 else v >> 32))
    v &= 0xFFFFFFFF
    parts.append(str(v - (1 << 32) if v >> 31 else v))
print(",".join(parts))
PY
)

SERVER_DIR=$(mktemp -d "${TMPDIR:-/tmp}/mekanism-security-smoke.XXXXXX")
LOG_FILE="$SERVER_DIR/server.log"
mkdir -p "$SERVER_DIR/mods"
cp "$LAUNCHER_JAR" "$SERVER_DIR/fabric-server-launch.jar"
if [[ -n ${FABRIC_SMOKE_RUNTIME_CACHE_DIR:-} ]]; then
  for part in libraries versions .fabric/server; do
    [[ -d "$FABRIC_SMOKE_RUNTIME_CACHE_DIR/$part" ]] || continue
    mkdir -p "$(dirname "$SERVER_DIR/$part")"
    cp -a --reflink=auto "$FABRIC_SMOKE_RUNTIME_CACHE_DIR/$part" "$SERVER_DIR/$part"
  done
fi
cp "$FABRIC_API_JAR" "$ENERGY_JAR" "${MOD_JARS[@]}" "$SERVER_DIR/mods/"
printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
PORT=$(python3 -c 'import socket;s=socket.socket();s.bind(("127.0.0.1",0));print(s.getsockname()[1]);s.close()')
printf 'online-mode=false\nserver-ip=127.0.0.1\nserver-port=%s\nspawn-protection=0\nmax-players=4\nview-distance=4\n' "$PORT" > "$SERVER_DIR/server.properties"

cd "$SERVER_DIR"
(
  for _ in $(seq 1 180); do
    grep -q 'Done (' server.log 2>/dev/null && break
    sleep 1
  done
  sleep 1
  echo "forceload add 0 0"
  echo "setblock 0 100 0 mekanism:enrichment_chamber"
  #Ownership and the private mode are what the access check reads; they are written the same way the machine scenarios
  #write their components, because /setblock places no item data.
  echo "data merge block 0 100 0 {component_security:{owner:[I;${OWNER_UUID_INTS}],security:1b}}"
  echo "data get block 0 100 0"
  sleep 2
  echo "say SECURITY_SERVER_READY"
  #Keep serving until the clients are done, so a slow Gradle client start cannot cut the scenario short.
  for _ in $(seq 1 900); do
    [[ -f "$SERVER_DIR/clients-done" ]] && break
    sleep 1
  done
  echo "stop"
  sleep 5
) | timeout "$TIMEOUT" java -Xms512M -Xmx2G -jar fabric-server-launch.jar nogui > server.log 2>&1 &
SERVER_PID=$!
echo "$SERVER_PID" > server.pid

for _ in $(seq 1 180); do
  grep -q 'SECURITY_SERVER_READY' server.log 2>/dev/null && break
  sleep 1
done
if ! grep -q 'SECURITY_SERVER_READY' server.log 2>/dev/null; then
  echo "fabric-security-smoke: server never became ready" >&2
  tail -20 server.log >&2
  exit 1
fi

#Clients run one at a time: the machine's owner is stored on the block, so each player's access is judged on its own
#and the two software-rendered clients never have to compete for the machine.
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
check "machine ownership was written to the block" "0, 100, 0 has the following block data.*component_security" "$LOG_FILE"
check "owner got the machine screen" 'SECURITY_FIXTURE_ALLOWED' "$ROOT/fabric/run/security-1/console.log"
check "intruder got no machine screen" 'SECURITY_FIXTURE_DENIED' "$ROOT/fabric/run/security-2/console.log"

if (( failures > 0 )); then
  echo "fabric-security-smoke: FAILED ($failures checks)" >&2
  exit 1
fi
echo "fabric-security-smoke: PASSED"
