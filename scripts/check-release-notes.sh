#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

gradle_file=app/build.gradle.kts
notes=RELEASE_NOTES.md

name="$(awk -F'"' '/versionName = "/ { print $2; exit }' "$gradle_file")"
if [ -z "$name" ]; then
  echo "app/build.gradle.kts has no versionName"
  exit 1
fi

if [ ! -f "$notes" ]; then
  echo "RELEASE_NOTES.md is missing"
  exit 1
fi

text="$(tr -d '\r' < "$notes")"
heading="$(awk 'NF { print; exit }' <<<"$text")"
if [ "$heading" != "# $name" ]; then
  echo "RELEASE_NOTES.md must start with '# $name' for this release"
  echo "found: ${heading:-<empty>}"
  exit 1
fi

body="$(awk '
  BEGIN { skip = 1 }
  skip && $0 ~ /^[[:space:]]*$/ { next }
  skip && $0 ~ /^# / { skip = 0; next }
  { print }
' <<<"$text")"
body="$(sed '/<!--/,/-->/d' <<<"$body")"
if ! awk 'BEGIN { found = 0 } /[^[:space:]]/ { found = 1; exit } END { exit !found }' <<<"$body"; then
  echo "RELEASE_NOTES.md must describe version $name"
  exit 1
fi
