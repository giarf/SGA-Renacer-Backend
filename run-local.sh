#!/usr/bin/env bash
set -euo pipefail

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

# Arranque estilo IntelliJ: java directo sobre classpath de Runtime
RAW_CP="$(sbt --batch --error 'export Runtime / fullClasspath' | tail -n 1)"
CP="${RAW_CP#\[info\] }"

exec java \
  -Dfile.encoding=UTF-8 \
  -Dsun.stdout.encoding=UTF-8 \
  -Dsun.stderr.encoding=UTF-8 \
  -classpath "$CP" \
  cl.familiarenacer.sga.api.SgaApiApp
