#!/usr/bin/env bash
# Make the android-components catalog path resolve in this repository layout.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
catalog="$repo_root/gradle/libs.versions.toml"
expected_root="$(cd "$repo_root/../.." && pwd)"
expected_dir="$expected_root/gradle"
expected_catalog="$expected_dir/libs.versions.toml"
version_file="$repo_root/version.txt"
expected_version="$repo_root/mobile/android/version.txt"
glean_venv="${GRADLE_GLEAN_PARSER_VENV:-$repo_root/.gradle/glean-parser-venv}"

if [[ ! -f "$catalog" ]]; then
  printf 'Missing version catalog: %s\n' "$catalog" >&2
  exit 1
fi
if [[ ! -f "$version_file" ]]; then
  printf 'Missing version file: %s\n' "$version_file" >&2
  exit 1
fi
if [[ ! -x "$glean_venv/bin/python" ]]; then
  printf 'Missing Glean parser environment: %s\n' "$glean_venv" >&2
  exit 1
fi

mkdir -p "$expected_dir"
if [[ -e "$expected_catalog" && ! -L "$expected_catalog" ]]; then
  printf 'Refusing to overwrite existing path: %s\n' "$expected_catalog" >&2
  exit 1
fi
ln -sfn "$catalog" "$expected_catalog"
printf 'android-components catalog: %s -> %s\n' "$expected_catalog" "$catalog"

mkdir -p "$(dirname "$expected_version")"
if [[ -e "$expected_version" && ! -L "$expected_version" ]]; then
  printf 'Refusing to overwrite existing path: %s\n' "$expected_version" >&2
  exit 1
fi
ln -sfn "$version_file" "$expected_version"
printf 'android-components version: %s -> %s\n' "$expected_version" "$version_file"
