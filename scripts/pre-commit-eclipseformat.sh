#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${ECLIPSE_EXE:-}" ]]; then
    echo 'ECLIPSE_EXE must be set for the eclipseformat hook' >&2
    exit 2
fi

exec bash "$(dirname "$0")/pre-commit-java-filelist.sh" eclipseformat -e "$ECLIPSE_EXE" --primary -- "$@"
