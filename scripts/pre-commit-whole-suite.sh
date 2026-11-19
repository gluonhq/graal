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
readarray -t suite_files < <(filter_suite_definition_files "$@")
combined=("${java_files[@]}" "${suite_files[@]}")
if [[ ${#combined[@]} -eq 0 ]]; then
    exit 0
fi

readarray -t suites < <(collect_unique_suites "${combined[@]}")
if [[ ${#suites[@]} -eq 0 ]]; then
    exit 0
fi

status=0
for suite in "${suites[@]}"; do
    run_and_record_status status run_mx_command "$suite" "$command_name" "${command_args[@]}"
done

exit "$status"
