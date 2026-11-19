#!/usr/bin/env bash
set -euo pipefail

exec bash "$(dirname "$0")/pre-commit-whole-suite.sh" verifysourceinproject -- "$@"
