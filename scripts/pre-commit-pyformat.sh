#!/usr/bin/env bash
set -euo pipefail

source "$(dirname "$0")/pre-commit-lib.sh"
ensure_repo_root

if [[ $# -eq 0 ]]; then
    exit 0
fi

readarray -t python_files < <(filter_pyformat_files "$@")
if [[ ${#python_files[@]} -eq 0 ]]; then
    exit 0
fi

declare -A files_by_suite=()
for file in "${python_files[@]}"; do
    if suite="$(file_suite "$file")"; then
        if has_suite_pyproject "$suite"; then
            files_by_suite["$suite"]+="$(realpath "$file")"$'\n'
        fi
    fi
done

if [[ ${#files_by_suite[@]} -eq 0 ]]; then
    exit 0
fi

status=0
for suite in "${!files_by_suite[@]}"; do
    mapfile -t suite_files < <(printf '%s' "${files_by_suite[$suite]}" | sed '/^$/d')
    if [[ ${#suite_files[@]} -eq 0 ]]; then
        continue
    fi
    run_and_record_status status run_mx_command "$suite" pyformat --dry-run "${suite_files[@]}"
done

exit "$status"
