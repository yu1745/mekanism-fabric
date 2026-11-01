#!/usr/bin/env bash
# W-120: real GUI access for owner, stranger and trusted player in an existing mdb owned session.
# Creates one temporary machine in air beside the player and removes it and its security frequency on exit.
set -euo pipefail
export FABRIC_SECURITY_MDB_SESSION=${FABRIC_SECURITY_MDB_SESSION:-dev}
export MDB=${MDB:-java -jar /home/wangyu/server-v2/develop/mcdebug-wt-121/mcdebug-cli.jar}
python3 - <<'PY'
import json
import math
import os
import shlex
import subprocess
import uuid

cli = shlex.split(os.environ['MDB']) + ['--session', os.environ['FABRIC_SECURITY_MDB_SESSION']]

def mdb(*args):
    result = json.loads(subprocess.run(cli + list(args), check=True, capture_output=True, text=True).stdout)
    if isinstance(result, dict) and result.get('ok') is False and args[:2] != ('gui', 'open-block'):
        raise RuntimeError(result)
    return result

def call(method, args=(), **target):
    flags = [flag for key, value in target.items() for flag in ('--' + key.rstrip('_').replace('_', '-'), str(value))]
    value = mdb('reflect', 'call', *flags, '--method', method, '--args', json.dumps(args))['value']
    return None if value == {'$null': True} else value

# Readiness checks reject stale discovery files before any mutation.
mdb('status')
mdb('gui', 'status')
xyz = [math.floor(mdb('reflect', 'get', '--root', 'player', '--path', method)['value'])
       for method in ('getX', 'getY', 'getZ')]
xyz[0] += 1
pos = ','.join(map(str, xyz))
coords = tuple(flag for axis, value in zip('xyz', xyz) for flag in ('--' + axis, str(value)))
original = mdb('world', 'get-block', *coords)
assert original['state']['name'] == 'minecraft:air', f'probe requires air beside player: {original}'
commands = call('getCommands', root='server')['ref']
source = call('createCommandSourceStack', root='server')['ref']

def command(text):
    value = call('performPrefixedCommand', [{'ref': source}, text], ref=commands)
    assert value is None or value > 0, (text, value)

def block_arg(path):
    return {'root': 'be', 'pos': xyz, 'path': path}

owner = uuid.uuid4()  # Never borrow or edit an existing player's security frequency.
ints = [(owner.int >> shift) & 0xFFFFFFFF for shift in (96, 64, 32, 0)]
owner_nbt = ','.join(str(i - 2**32 if i >= 2**31 else i) for i in ints)
created = False
frequency = None
manager = None
owner_arg = block_arg('securityComponent.ownerUUID')

def mode(value):
    command(f'data merge block {xyz[0]} {xyz[1]} {xyz[2]} {{component_security:{{security_mode:{value}}}}}')
    mdb('gui', 'settle')

def check(label, allowed):
    mdb('gui', 'close')
    result = mdb('gui', 'open-block', *coords)
    assert result['ok'] is allowed, (label, result)
    if allowed:
        assert result.get('screen') == 'mekanism.client.gui.machine.GuiElectricMachine', result
    else:
        assert result.get('reason') == 'NO_GUI_CHANGED', result
    print(f'PASS {label} {json.dumps(result, separators=(",", ":"))}', flush=True)
    mdb('gui', 'close')

try:
    mdb('world', 'set-block', *coords, '--block', 'mekanism:enrichment_chamber')
    created = True
    command(f'data merge block {xyz[0]} {xyz[1]} {xyz[2]} {{component_security:{{owner:[I;{owner_nbt}],security_mode:1}}}}')
    # deserialize writes owner only; use the real setter to attach/create the owner's security frequency.
    call('setOwnerUUID', [owner_arg], root='be', pos=pos, path='securityComponent')
    frequency = call('getFrequency', root='be', pos=pos, path='securityComponent')['ref']
    manager = call('getPublicManager', class_='mekanism.common.lib.frequency.FrequencyType',
                   path='SECURITY.managerWrapper')['ref']
    check('PRIVATE stranger denied', False)
    mode(2)
    check('TRUSTED unlisted player denied', False)
    call('addTrusted', [{'root': 'player', 'path': 'uuid'}, 'W120 probe player'], ref=frequency)
    check('TRUSTED listed player allowed', True)
    call('removeTrusted', [0], ref=frequency)
    check('TRUSTED removed player denied', False)
    mode(1)
    player_uuid = uuid.UUID(mdb('reflect', 'get', '--root', 'player', '--path', 'uuid')['value'])
    player_ints = [(player_uuid.int >> shift) & 0xFFFFFFFF for shift in (96, 64, 32, 0)]
    player_nbt = ','.join(str(i - 2**32 if i >= 2**31 else i) for i in player_ints)
    command(f'data merge block {xyz[0]} {xyz[1]} {xyz[2]} {{component_security:{{owner:[I;{player_nbt}]}}}}')
    check('PRIVATE owner allowed', True)
finally:
    mdb('gui', 'close')
    if created:
        if manager is not None:
            # Keep the UUID object reachable while removing only this probe's frequency.
            command(f'data merge block {xyz[0]} {xyz[1]} {xyz[2]} {{component_security:{{owner:[I;{owner_nbt}]}}}}')
            assert call('remove', [owner_arg, owner_arg], ref=manager) is True
            assert call('getFrequency', [owner_arg], ref=manager) is None
        mdb('world', 'set-block', *coords, '--block', 'minecraft:air')
print('fabric-security-denial-probe: PASSED')
PY
