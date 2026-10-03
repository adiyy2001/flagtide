#!/bin/sh
set -eu
cat > /usr/share/nginx/html/config.json <<EOF
{
  "apiUrl": "${FLAGWIRE_ADMIN_API_URL}",
  "project": "${FLAGWIRE_ADMIN_PROJECT}",
  "adminKeys": ${FLAGWIRE_ADMIN_KEYS}
}
EOF
