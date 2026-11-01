#!/usr/bin/env bash
# Retired: the old dual-client fixture launched game clients outside mdb ownership.
# W-120 is covered by the real owner/stranger/trusted/revocation GUI probe in an existing owned session.
set -euo pipefail
printf '%s\n' 'fabric-security-denial-smoke: retired; use scripts/fabric-security-denial-probe.sh with an mdb owned session' >&2
exit 2
