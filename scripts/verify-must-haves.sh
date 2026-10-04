#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

export FLAGTIDE_UID="$(id -u)"
export FLAGTIDE_GID="$(id -g)"

docker compose up -d --build --wait
node scripts/verify-must-haves.mjs
