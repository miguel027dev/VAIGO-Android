#!/usr/bin/env bash
set -e
rm -rf app/src/main/java/online/vaigo
find app/src/main/java -type d -empty -delete 2>/dev/null || true
echo "Legacy online/vaigo package removed."
