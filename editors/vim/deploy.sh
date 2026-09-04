#!/usr/bin/env bash
# Deploy the Vim/Neovim plugin to its standalone distribution repository.
#
# Model (mirrors "mvn deploy" to Maven Central): the monorepo's editors/vim/ is the
# SOURCE OF TRUTH; this script PUBLISHES its current content to the root of the
# standalone plugin repo so users can install with a clean one-liner and a tiny clone
# (instead of cloning the whole monorepo via an rtp subdir).
#
# It is a content deploy, not a history mirror: the plugin repo gets the current
# artifact plus a commit that records which monorepo commit it came from. The plugin
# repo's own LICENSE is preserved.
#
# Usage:  editors/vim/deploy.sh [--dry-run]
set -euo pipefail

PLUGIN_REMOTE="git@github.com:verhasi/java-composition.vim.git"
PLUGIN_BRANCH="master"

HERE="$(cd "$(dirname "$0")" && pwd)"        # editors/vim
MONOREPO_ROOT="$(cd "$HERE/../.." && pwd)"
SRC="$HERE"                                   # what we publish (editors/vim contents)

DRY_RUN=0
[ "${1:-}" = "--dry-run" ] && DRY_RUN=1

# Record provenance: the monorepo commit this deploy is built from.
SRC_COMMIT="$(git -C "$MONOREPO_ROOT" rev-parse --short HEAD)"
SRC_DIRTY=""
if ! git -C "$MONOREPO_ROOT" diff --quiet || ! git -C "$MONOREPO_ROOT" diff --cached --quiet; then
  SRC_DIRTY=" (with uncommitted changes)"
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "==> Cloning plugin repo ($PLUGIN_REMOTE, $PLUGIN_BRANCH)"
git clone --depth 1 --branch "$PLUGIN_BRANCH" "$PLUGIN_REMOTE" "$WORK/repo"

echo "==> Syncing editors/vim/ content into the plugin repo root"
# Replace everything EXCEPT the repo's own .git and LICENSE, then copy our content.
find "$WORK/repo" -mindepth 1 -maxdepth 1 \
  ! -name '.git' ! -name 'LICENSE' -exec rm -rf {} +

# Copy the plugin content. Exclude the deploy script (it belongs to the monorepo)
# and any editor/OS junk (swap files, .DS_Store) that must never reach the artifact.
( cd "$SRC" && rsync -a \
    --exclude 'deploy.sh' \
    --exclude '*.swp' \
    --exclude '*.swo' \
    --exclude '.*.sw?' \
    --exclude '.DS_Store' \
    ./ "$WORK/repo/" )

cd "$WORK/repo"
git add -A

if git diff --cached --quiet; then
  echo "==> No changes to deploy (plugin repo already up to date)."
  exit 0
fi

MSG="Deploy from monorepo $SRC_COMMIT$SRC_DIRTY

Published from editors/vim/ of verhasi/java-composition@$SRC_COMMIT.
Source of truth is the monorepo; this repo is the distribution artifact."

if [ "$DRY_RUN" -eq 1 ]; then
  echo "==> DRY RUN — would commit and push the following changes:"
  git status --short
  echo "--- commit message ---"; echo "$MSG"
  exit 0
fi

git commit -q -m "$MSG"
git push origin "$PLUGIN_BRANCH"
echo "==> Deployed to $PLUGIN_REMOTE ($PLUGIN_BRANCH)."
