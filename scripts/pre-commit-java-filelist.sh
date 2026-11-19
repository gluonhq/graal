#!/usr/bin/env bash
set -euo pipefail

source "$(dirname "$0")/pre-commit-lib.sh"
ensure_repo_root

if [[ $# -eq 0 ]]; then
    exit 0
fi

command_name="$1"
shift

command_args=()
while (($#)); do
    if [[ "$1" == "--" ]]; then
        shift
        break
    fi
    command_args+=("$1")
    shift
 done

if [[ -z "$command_name" ]]; then
    echo "mx command is required" >&2
    exit 2
fi

readarray -t java_files < <(filter_java_files "$@")
if [[ ${#java_files[@]} -eq 0 ]]; then
    exit 0
fi

declare -A files_by_suite=()
for file in "${java_files[@]}"; do
    if suite="$(file_suite "$file")"; then
        files_by_suite["$suite"]+="$(realpath "$file")"$'\n'
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

    run_and_record_status status run_mx_command "$suite" "$command_name" "${command_args[@]}" --filelist "$filelist"

    rm -f "$filelist"
done

exit "$status"
