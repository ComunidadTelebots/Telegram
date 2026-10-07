#!/bin/sh
set -eu
cd /src
if [ -n "${RELEASE_VERSION:-}" ]; then
    node -e 'if (require("./package.json").version !== process.env.RELEASE_VERSION) process.exit(1)'
fi
npm ci --legacy-peer-deps --ignore-scripts --no-audit --no-fund
mode="$CHANNEL"
export VITE_RELEASE_CHANNEL="$CHANNEL"
[ "$mode" != alfa ] || mode=alpha
[ "$mode" != dev ] || mode=development
npm run build -- --mode "$mode"
test -s build/index.html
tar -czf /out/application.tar.gz -C build .
