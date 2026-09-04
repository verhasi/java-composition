#!/usr/bin/env bash
# Regression test for the concise-method-body Vim syntax add-on.
# Installs the add-on into a throwaway HOME, opens the sample file, probes the
# syntax groups Vim assigns, and asserts the expected results.
#
# Exit 0 on ALL_PASS, non-zero otherwise. Prints the per-check results.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"        # editors/vim/test
VIMDIR="$(cd "$HERE/.." && pwd)"             # editors/vim
SAMPLE="$VIMDIR/sample-concise.java"
TESTVIM="$HERE/syntax_test.vim"

TMPHOME="$(mktemp -d)"
OUT="$(mktemp)"
trap 'rm -rf "$TMPHOME" "$OUT" "$RC"' EXIT

mkdir -p "$TMPHOME/.vim/after/syntax"
cp "$VIMDIR/after/syntax/java.vim" "$TMPHOME/.vim/after/syntax/java.vim"

RC="$(mktemp)"
cat > "$RC" <<'VIMRC'
set nocompatible
filetype plugin indent on
syntax on
VIMRC

# Run headless. The add-on is AUTO-LOADED from $TMPHOME/.vim/after/syntax/java.vim
# (the real install path) — NOT explicitly sourced — so the test also guards the
# regression where a second load of the standard java.vim cleared our items.
#
# Use a private COPY of the sample and disable swap files (-n): otherwise, if the
# repo's sample-concise.java is open in an interactive Vim, its stale .swp file
# makes headless Vim halt on the E325 swap prompt and the test spuriously fails.
SAMPLE_COPY="$TMPHOME/sample-concise.java"
cp "$SAMPLE" "$SAMPLE_COPY"

HOME="$TMPHOME" vim -N -n -u "$RC" -i NONE \
  -c "edit $SAMPLE_COPY" \
  -c "set filetype=java" \
  -c "source $TESTVIM" \
  -c "call RunConciseSyntaxTests('$OUT')" \
  -c "qa!" </dev/null >/dev/null 2>&1 || true

echo "=== concise Vim syntax test ==="
cat "$OUT"

if grep -q '^ALL_PASS$' "$OUT"; then
  echo "RESULT: PASS"
  exit 0
else
  echo "RESULT: FAIL"
  exit 1
fi
