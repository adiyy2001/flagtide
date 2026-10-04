#!/usr/bin/env bash
set -euo pipefail

base="http://127.0.0.1:${FLAGTIDE_PORT_SERVER_A:-18081}"
project="${FLAGTIDE_SMOKE_PROJECT:-demo}"
admin="${FLAGTIDE_SMOKE_DEV_ADMIN_KEY:-fwa_demo_dev_admin_000000000000}"
sdk="${FLAGTIDE_SMOKE_DEV_SDK_KEY:-fws_demo_dev_sdk_0000000000000}"
flag="smoke-$(date +%s)-$$"
flags="$base/api/v1/projects/$project/flags"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

failures=0

check() {
  local name="$1" expected="$2" actual="$3"
  if [[ "$expected" == "$actual" ]]; then
    printf 'ok   %s\n' "$name"
  else
    printf 'FAIL %s: expected %s, got %s\n' "$name" "$expected" "$actual"
    failures=$((failures + 1))
  fi
}

contains() {
  local name="$1" needle="$2" file="$3"
  if grep -q -- "$needle" "$file"; then
    printf 'ok   %s\n' "$name"
  else
    printf 'FAIL %s: %s not found in %s\n' "$name" "$needle" "$(head -c 300 "$file")"
    failures=$((failures + 1))
  fi
}

call() {
  local out="$1"
  shift
  curl -sS -o "$out" -D "$out.headers" -w '%{http_code}' "$@"
}

etag_of() {
  tr -d '\r' <"$1.headers" | awk 'tolower($1) == "etag:" { print $2 }'
}

for attempt in $(seq 1 30); do
  if curl -fsS "$base/q/health/ready" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

check "ready" 200 "$(call "$work/ready" "$base/q/health/ready")"
check "openapi is served" 200 "$(call "$work/openapi" "$base/q/openapi?format=json")"
check "no key is rejected" 401 "$(call "$work/anon" "$flags")"
check "sdk key cannot use the admin api" 403 "$(call "$work/forbidden" -H "Authorization: Bearer $sdk" "$flags")"
check "project is readable" 200 "$(call "$work/project" -H "Authorization: Bearer $admin" "$base/api/v1/projects/$project")"

created="$(call "$work/created" -X POST -H "Authorization: Bearer $admin" -H 'Content-Type: application/json' \
  -d "{\"key\":\"$flag\",\"description\":\"smoke\",\"type\":\"boolean\",\"variants\":[{\"key\":\"on\",\"value\":true},{\"key\":\"off\",\"value\":false}],\"offVariant\":\"off\",\"fallthroughVariant\":\"on\"}" \
  "$flags")"
check "flag is created" 201 "$created"
check "created flag has revision 1" '"1"' "$(etag_of "$work/created")"

toggle="$flags/$flag/environments/dev/enabled"
first="$(call "$work/first" -X PUT -H "Authorization: Bearer $admin" -H 'Content-Type: application/json' -H 'If-Match: "1"' -d '{"enabled":true}' "$toggle")"
check "toggle with the current revision" 200 "$first"
check "toggle bumps the revision" '"2"' "$(etag_of "$work/first")"

stale="$(call "$work/stale" -X PUT -H "Authorization: Bearer $admin" -H 'Content-Type: application/json' -H 'If-Match: "1"' -d '{"enabled":false}' "$toggle")"
check "stale revision conflicts" 409 "$stale"
contains "conflict is problem details" 'urn:flagtide:problem' "$work/stale"

check "malformed If-Match is a bad request" 400 \
  "$(call "$work/malformed" -X PUT -H "Authorization: Bearer $admin" -H 'Content-Type: application/json' -H 'If-Match: three' -d '{"enabled":true}' "$toggle")"

check "unknown property is rejected" 400 \
  "$(call "$work/unknown" -X PUT -H "Authorization: Bearer $admin" -H 'Content-Type: application/json' -d '{"enabled":true,"surprise":1}' "$toggle")"

check "kill switch engages" 200 "$(call "$work/kill" -X POST -H "Authorization: Bearer $admin" "$flags/$flag/environments/dev/kill-switch")"
check "kill switch releases" 200 "$(call "$work/release" -X DELETE -H "Authorization: Bearer $admin" "$flags/$flag/environments/dev/kill-switch")"

check "snapshot is served to the sdk key" 200 "$(call "$work/snapshot" -H "Authorization: Bearer $sdk" "$base/sdk/v1/snapshot")"
contains "snapshot carries the flag" "$flag" "$work/snapshot"
version="$(etag_of "$work/snapshot")"
check "snapshot answers 304 for its own version" 304 \
  "$(call "$work/unchanged" -H "Authorization: Bearer $sdk" -H "If-None-Match: $version" "$base/sdk/v1/snapshot")"

check "audit log is readable" 200 "$(call "$work/audit" -H "Authorization: Bearer $admin" "$base/api/v1/projects/$project/audit")"
contains "audit log records the flag" "$flag" "$work/audit"

if [[ "$failures" -gt 0 ]]; then
  printf '%s check(s) failed\n' "$failures"
  exit 1
fi
printf 'all checks passed\n'
