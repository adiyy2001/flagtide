#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

export CHROME_PATH="${CHROME_PATH:-/usr/bin/google-chrome}"
version="${LIGHTHOUSE_VERSION:-13.5.0}"
minimum="${LIGHTHOUSE_MIN_ACCESSIBILITY:-95}"
admin="http://127.0.0.1:${FLAGTIDE_PORT_ADMIN:-14200}"
shop="http://127.0.0.1:${FLAGTIDE_PORT_DEMO_SHOP:-14300}"
out="bench/results/tmp/lighthouse"
mkdir -p "$out"
rm -f "$out"/*.json

profile="$(mktemp -d)"
debug_port="${LIGHTHOUSE_DEBUG_PORT:-19222}"
"$CHROME_PATH" --headless=new --no-sandbox --disable-gpu --remote-debugging-address=127.0.0.1 \
  --remote-debugging-port="$debug_port" --user-data-dir="$profile" about:blank >/dev/null 2>&1 &
chrome_pid=$!
trap 'kill "$chrome_pid" 2>/dev/null || true; wait "$chrome_pid" 2>/dev/null || true; rm -rf "$profile" || true' EXIT
for attempt in $(seq 1 50); do
  if curl -fsS "http://127.0.0.1:$debug_port/json/version" >/dev/null 2>&1; then
    break
  fi
  sleep 0.2
done

pages=(
  "admin-flags|$admin/flags"
  "admin-flag-editor|$admin/flags/promo-banner"
  "admin-segments|$admin/segments"
  "admin-environments|$admin/environments"
  "admin-audit|$admin/audit"
  "admin-propagation|$admin/propagation"
  "shop-storefront|$shop/"
  "shop-recommendations|$shop/recommendations?visitor=visitor-beta"
)

for entry in "${pages[@]}"; do
  name="${entry%%|*}"
  url="${entry#*|}"
  npx --yes "lighthouse@$version" "$url" \
    --only-categories=accessibility,best-practices \
    --port="$debug_port" \
    --output=json --output-path="$out/$name.json" --quiet
done

node bench/lighthouse-report.mjs "$out" bench/results/lighthouse.json "$minimum"
