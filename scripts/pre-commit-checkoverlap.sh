#!/usr/bin/env bash
set -euo pipefail

source "$(dirname "$0")/pre-commit-lib.sh"
ensure_repo_root

if [[ $# -eq 0 ]]; then
    exit 0
fi

readarray -t suite_files < <(filter_suite_definition_files "$@")
if [[ ${#suite_files[@]} -eq 0 ]]; then
    exit 0
fi

readarray -t suites < <(collect_unique_suites "${suite_files[@]}")
if [[ ${#suites[@]} -eq 0 ]]; then
    exit 0
fi

status=0
for suite in "${suites[@]}"; do
    run_and_record_status status run_mx_command "$suite" checkoverlap
done

exit "$status"
