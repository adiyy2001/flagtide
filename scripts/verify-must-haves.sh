#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

export FLAGWIRE_UID="$(id -u)"
export FLAGWIRE_GID="$(id -g)"

docker compose up -d --build --wait
node scripts/verify-must-haves.mjs
