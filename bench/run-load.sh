#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

export FLAGTIDE_UID="$(id -u)"
export FLAGTIDE_GID="$(id -g)"

rm -f bench/results/k6-summary.json
docker compose up -d --build --wait postgres server-a server-b
docker compose --profile load run --rm k6 || status=$?
mkdir -p bench/results/tmp
curl -fsS -H 'Authorization: Bearer fwa_demo_dev_admin_000000000000' \
  "http://127.0.0.1:${FLAGTIDE_PORT_SERVER_B:-18082}/api/v1/projects/demo/environments/dev/propagation" \
  >bench/results/tmp/propagation-server.json
node bench/load-report.mjs bench/results/k6-summary.json bench/results/propagation-k6.json bench/results/tmp/propagation-server.json
exit "${status:-0}"
