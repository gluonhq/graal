#!/usr/bin/env bash
set -euo pipefail

unset GIT_DIR

repo_root() {
    git rev-parse --show-toplevel
}

ensure_repo_root() {
    cd "$(repo_root)"
}

is_suite_dir() {
    local dir="$1"
    compgen -G "$dir/mx.*/suite.py" > /dev/null
}

file_suite() {
    local file="$1"
    local first_segment

    first_segment="${file%%/*}"
    if [[ "$first_segment" == "$file" ]]; then
        return 1
    fi
    if is_suite_dir "$first_segment"; then
        printf '%s\n' "$first_segment"
        return 0
    fi
    return 1
}

has_suite_pyproject() {
    local suite="$1"
    [[ -f "$suite/pyproject.toml" ]]
}

filter_java_files() {
    local file
    for file in "$@"; do
        if [[ "$file" == *.java && -f "$file" ]]; then
            printf '%s\n' "$file"
        fi
    done
}

filter_python_files() {
    local file
    for file in "$@"; do
        if [[ "$file" == *.py && -f "$file" ]]; then
            printf '%s\n' "$file"
        fi
    done
}

filter_mx_python_files() {
    local file
    for file in "$@"; do
        if [[ "$file" == */mx.*/*.py && -f "$file" ]]; then
            printf '%s\n' "$file"
        fi
    done
}

filter_suite_definition_files() {
    local file
    for file in "$@"; do
        if [[ "$file" == */mx.*/* && "$file" == */suite.py && -f "$file" ]]; then
            printf '%s\n' "$file"
        fi
    done
}

filter_pyformat_files() {
    local file
    for file in "$@"; do
        if [[ "$file" != *.py || ! -f "$file" ]]; then
            continue
        fi
        if [[ "$file" == truffle/mx.truffle/mx_polybench/*.py ]]; then
            printf '%s\n' "$file"
            continue
        fi
        if [[ "$file" == web-image/*.py ]]; then
            printf '%s\n' "$file"
        fi
    done
}

collect_unique_suites() {
    local file
    local -A seen=()

    for file in "$@"; do
        local suite
        if suite="$(file_suite "$file")"; then
            if [[ -z "${seen[$suite]:-}" ]]; then
                seen[$suite]=1
                printf '%s\n' "$suite"
            fi
        fi
    done
}

run_mx_command() {
    local suite="$1"
    shift

    echo "Running: mx -p $suite $*"
    mx -p "$suite" "$@"
}

run_and_record_status() {
    local -n overall_status_ref="$1"
    shift

    if "$@"; then
        return 0
    else
        local rc=$?
        overall_status_ref=$rc
    fi
    return 0
}
