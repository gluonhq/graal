#!/usr/bin/env bash
set -euo pipefail

exec bash "$(dirname "$0")/pre-commit-java-filelist.sh" checkstyle --primary -- "$@"
