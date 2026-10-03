#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

export FLAGWIRE_UID="$(id -u)"
export FLAGWIRE_GID="$(id -g)"

rm -f bench/results/k6-summary.json
docker compose up -d --build --wait postgres server-a server-b
docker compose --profile load run --rm k6 || status=$?
node bench/load-report.mjs bench/results/k6-summary.json bench/results/propagation-k6.json
exit "${status:-0}"
