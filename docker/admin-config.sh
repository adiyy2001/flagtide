#!/bin/sh
set -eu
cat > /usr/share/nginx/html/config.json <<EOF
{
  "apiUrl": "${FLAGTIDE_ADMIN_API_URL}",
  "project": "${FLAGTIDE_ADMIN_PROJECT}",
  "adminKeys": ${FLAGTIDE_ADMIN_KEYS}
}
EOF
