#!/usr/bin/env bash
# Two-player security check (W-120), driven through an mdb session.
#
# One machine, two identities: the probe points the machine's owner at another player first and expects the server to
# refuse to open the screen, then points it at the player who is actually playing and expects the screen to open. Only
# the owner changes between the two attempts, so a refusal has to come from the access check and not from a broken
# interaction path.
#
# Requires a working mdb client session (`mdb session start dev ...`) and an X display for the client.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
MDB=${MDB:-java -jar /home/wangyu/server-v2/develop/mcdebug-wt-121/mcdebug-cli.jar}
SESSION=${FABRIC_SECURITY_MDB_SESSION:-dev}
BLOCK=${FABRIC_SECURITY_BLOCK:-mekanism:enrichment_chamber}

failures=0
pass() { printf 'PASS %s\n' "$1"; }
fail() { printf 'FAIL %s\n' "$1"; failures=$((failures + 1)); }

mdb() { $MDB --session "$SESSION" "$@"; }

json_field() { python3 -c "import json,sys;d=json.load(sys.stdin);print($1)"; }

#A machine next to the player, private, owned by somebody else.
mdb world set-block --x -18 --y 89 --z -303 --block "$BLOCK" >/dev/null

read -r COMMANDS_REF SOURCE_REF PLAYER_UUID <<<"$(
  python3 - "$SESSION" <<'PY'
import json, subprocess, sys

def call(args):
    out = subprocess.run(args, capture_output=True, text=True, check=True).stdout
    return json.loads(out)

mdb = ["java", "-jar", "/home/wangyu/server-v2/develop/mcdebug-wt-121/mcdebug-cli.jar", "--session", sys.argv[1]]
commands = call(mdb + ["reflect", "call", "--root", "server", "--path", "", "--method", "getCommands"])
source = call(mdb + ["reflect", "call", "--root", "server", "--method", "createCommandSourceStack", "--path", ""])
player = call(mdb + ["reflect", "get", "--root", "player", "--path", "uuid"])
print(commands["value"]["ref"], source["value"]["ref"], player["value"])
PY
)"

uuid_ints() {
  python3 - "$1" <<'PY'
import sys, uuid
u = uuid.UUID(sys.argv[1])
ints = [(u.int >> shift) & 0xFFFFFFFF for shift in (96, 64, 32, 0)]
print(",".join(str(i - (1 << 32) if i >= (1 << 31) else i) for i in ints))
PY
}

#Offline UUID of a second player, so the two attempts really involve two identities.
OTHER_UUID=$(python3 - <<'PY'
import hashlib, uuid
digest = bytearray(hashlib.md5(b"OfflinePlayer:Bob").digest())
digest[6] = (digest[6] & 0x0f) | 0x30
digest[8] = (digest[8] & 0x3f) | 0x80
print(uuid.UUID(bytes=bytes(digest)))
PY
)

owner_command() {
  #The mode key is security_mode and it is an integer: a byte is silently dropped and the machine stays public.
  printf 'data merge block -18 89 -303 {component_security:{owner:[I;%s],security_mode:1}}' "$(uuid_ints "$1")"
}

set_owner() {
  $MDB --session "$SESSION" reflect call --ref "$COMMANDS_REF" --method performPrefixedCommand \
    --args "[{\"ref\":$SOURCE_REF},\"$(owner_command "$1")\"]" >/dev/null
  sleep 1
}

#Attempt one: owned by another player.
set_owner "$OTHER_UUID"
mdb gui close >/dev/null 2>&1 || true
denied=$(mdb gui open-block --x -18 --y 89 --z -303)
if [[ $(printf '%s' "$denied" | json_field "d['ok']") == False ]]; then
  pass "machine owned by another player refuses to open ($(printf '%s' "$denied" | json_field "d['reason']"))"
else
  fail "machine owned by another player opened: $denied"
fi

#Attempt two: owned by the player who is actually playing.
set_owner "$PLAYER_UUID"
opened=$(mdb gui open-block --x -18 --y 89 --z -303)
if [[ $(printf '%s' "$opened" | json_field "d['ok']") == True ]]; then
  pass "machine owned by the player opens ($(printf '%s' "$opened" | json_field "d['screen']"))"
else
  fail "machine owned by the player stayed closed: $opened"
fi
mdb gui close >/dev/null 2>&1 || true

if (( failures > 0 )); then
  echo "fabric-security-denial-probe: FAILED ($failures checks)" >&2
  exit 1
fi
echo "fabric-security-denial-probe: PASSED"
