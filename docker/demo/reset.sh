#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."
project=flagtide-demo
compose=(docker compose -p "$project" -f compose.yaml -f docker/demo/compose.yaml)

"${compose[@]}" rm --stop --force postgres server-a server-b seed
docker volume rm "${project}_postgres-data"
"${compose[@]}" up -d --wait --no-build
echo "$(date -Is) flagtide demo reset to the seed"
