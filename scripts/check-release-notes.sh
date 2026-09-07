#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

gradle_file=app/build.gradle.kts
notes=RELEASE_NOTES.md

name="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$gradle_file" | head -1)"
if [ -z "$name" ]; then
  echo "app/build.gradle.kts has no versionName"
  exit 1
fi

if [ ! -f "$notes" ]; then
  echo "RELEASE_NOTES.md is missing"
  exit 1
fi

text="$(tr -d '\r' < "$notes")"
heading="$(printf '%s\n' "$text" | sed '/^[[:space:]]*$/d' | head -1)"
if [ "$heading" != "# $name" ]; then
  echo "RELEASE_NOTES.md must start with '# $name' for this release"
  echo "found: ${heading:-<empty>}"
  exit 1
fi

body="$(printf '%s\n' "$text" | awk '
  BEGIN { skip = 1 }
  skip && $0 ~ /^[[:space:]]*$/ { next }
  skip && $0 ~ /^# / { skip = 0; next }
  { print }
')"
body="$(printf '%s\n' "$body" | sed '/<!--/,/-->/d')"
if ! printf '%s\n' "$body" | grep -q '[^[:space:]]'; then
  echo "RELEASE_NOTES.md must describe version $name"
  exit 1
fi
