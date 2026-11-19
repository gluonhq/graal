#!/usr/bin/env bash
set -euo pipefail

exec bash "$(dirname "$0")/pre-commit-whole-suite.sh" spotbugs --strict-mode --primary -- "$@"
