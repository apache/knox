#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to you under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
TESTS_DIR="${REPO_ROOT}/.github/workflows/tests"
TESTS_REL=".github/workflows/tests"
VENV_DIR="${TESTS_DIR}/.venv"
PYLINT="${VENV_DIR}/bin/pylint"
ZERO_SHA="0000000000000000000000000000000000000000"

export PYLINTHOME="${TESTS_DIR}/.pylint_cache"
FILES_LIST=""

ensure_venv() {
  if [[ ! -x "${PYLINT}" ]]; then
    echo "Creating pylint venv at ${VENV_DIR}..."
    python3 -m venv "${VENV_DIR}"
    "${VENV_DIR}/bin/pip" install -q \
      -r "${TESTS_DIR}/requirements.txt" \
      "pylint==4.0.5"
  fi
}

resolve_upstream_sha() {
  if git -C "${REPO_ROOT}" rev-parse --verify origin/HEAD >/dev/null 2>&1; then
    git -C "${REPO_ROOT}" rev-parse origin/HEAD
    return
  fi
  if git -C "${REPO_ROOT}" rev-parse --verify origin/main >/dev/null 2>&1; then
    git -C "${REPO_ROOT}" rev-parse origin/main
    return
  fi
  if git -C "${REPO_ROOT}" rev-parse --verify origin/master >/dev/null 2>&1; then
    git -C "${REPO_ROOT}" rev-parse origin/master
    return
  fi
  git -C "${REPO_ROOT}" rev-list --max-parents=0 HEAD | tail -1
}

filter_tests_python_files() {
  while IFS= read -r file; do
    [[ -z "${file}" ]] && continue
    [[ "${file}" != "${TESTS_REL}/"* ]] && continue
    [[ "${file}" != *.py ]] && continue
    [[ -f "${REPO_ROOT}/${file}" ]] || continue
    printf '%s\n' "${REPO_ROOT}/${file}"
  done | sort -u
}

collect_push_files() {
  local local_ref local_sha remote_ref remote_sha remote_base

  while read -r local_ref local_sha remote_ref remote_sha; do
    [[ "${local_sha}" == "${ZERO_SHA}" ]] && continue

    if [[ "${remote_sha}" == "${ZERO_SHA}" ]]; then
      remote_sha="$(resolve_upstream_sha)"
    fi

    git -C "${REPO_ROOT}" diff --name-only --diff-filter=ACMR \
      "${remote_sha}" "${local_sha}" -- "${TESTS_REL}/"
  done | filter_tests_python_files
}

collect_worktree_files() {
  {
    git -C "${REPO_ROOT}" diff --name-only --diff-filter=ACMR HEAD -- "${TESTS_REL}/"
    git -C "${REPO_ROOT}" diff --name-only --diff-filter=ACMR --cached HEAD -- "${TESTS_REL}/"
  } | sort -u | filter_tests_python_files
}

run_pylint() {
  local files_file="$1"
  local file_count

  file_count="$(wc -l < "${files_file}" | tr -d ' ')"
  if [[ "${file_count}" -eq 0 ]]; then
    echo "No modified Python files under ${TESTS_REL}; skipping pylint."
    return 0
  fi

  ensure_venv

  echo "Running pylint on ${file_count} file(s) under ${TESTS_REL}:"
  sed "s|^${REPO_ROOT}/|  |" "${files_file}"

  (
    cd "${TESTS_DIR}"
    local pylint_args=()
    while IFS= read -r file; do
      [[ -n "${file}" ]] && pylint_args+=("${file}")
    done < "${files_file}"
    "${PYLINT}" --rcfile="${TESTS_DIR}/.pylintrc" "${pylint_args[@]}"
  )
}

main() {
  local mode="${1:-worktree}"

  FILES_LIST="$(mktemp)"
  trap 'rm -f "${FILES_LIST}"' EXIT

  if [[ "${mode}" == "pre-push" ]]; then
    collect_push_files > "${FILES_LIST}"
  else
    collect_worktree_files > "${FILES_LIST}"
  fi

  run_pylint "${FILES_LIST}"
}

main "$@"
