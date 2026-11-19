#!/usr/bin/env bash
set -euo pipefail

source "$(dirname "$0")/pre-commit-lib.sh"
ensure_repo_root

if [[ $# -eq 0 ]]; then
    exit 0
fi

readarray -t existing_files < <(
    local_file() {
        local file
        for file in "$@"; do
            if [[ -f "$file" ]]; then
                printf '%s\n' "$file"
            fi
        done
    }
    local_file "$@"
)
if [[ ${#existing_files[@]} -eq 0 ]]; then
    exit 0
fi

declare -A files_by_suite=()
for file in "${existing_files[@]}"; do
    if suite="$(file_suite "$file")"; then
        files_by_suite["$suite"]+="${file#"$suite"/}"$'\n'
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

    filelist="$(mktemp)"
    printf '%s\n' "${suite_files[@]}" > "$filelist"
    run_and_record_status status run_mx_command "$suite" checkcopyrights --primary -- --file-list "$filelist"
    rm -f "$filelist"
done

exit "$status"
