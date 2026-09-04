# Plan: Deploy the Vim Plugin from CI (Bitbucket → GitHub)

## Goal

Publish the Vim/Neovim plugin artifact automatically during the release, the same way the
Java artifacts go to Maven Central — so a release ships both. The monorepo `editors/vim/`
stays the source of truth; `editors/vim/deploy.sh` publishes its content to the standalone
plugin repo `verhasi/java-composition.vim` (branch `master`).

## Status

`deploy.sh` is already CI-ready in shape: non-interactive, idempotent (no-ops when nothing
changed), has `--dry-run`, excludes junk (swap/OS files, itself), and preserves the plugin
repo's LICENSE. Running it locally works today via the developer's SSH key.

The one genuinely new problem for CI is **authentication**: the pipeline runs on
**Bitbucket**, but `deploy.sh` pushes to a **GitHub** repo — a cross-host write.

## The cross-host auth problem

`deploy.sh` uses the SSH URL `git@github.com:verhasi/java-composition.vim.git`, which works
on the developer laptop via a personal SSH key. A Bitbucket runner has no such key and no
GitHub identity. Three things must be provided in CI:

1. **A GitHub write credential** scoped to just the plugin repo (least privilege).
2. **A CI git identity** (`user.name` / `user.email`) for the deploy commit — the script
   currently relies on ambient git config.
3. **Release-only gating** so the deploy runs on the release step (Bitbucket `master`) only,
   not on every dev build.

### Credential options (pick one)

| Option | Mechanism | Pros | Cons |
|---|---|---|---|
| **A. Fine-grained PAT (HTTPS)** — recommended for simplicity | GitHub fine-grained Personal Access Token scoped to `verhasi/java-composition.vim` with `contents: write` only; stored as a **secured** Bitbucket repo variable; push via `https://x-access-token:${TOKEN}@github.com/verhasi/java-composition.vim.git` | No `ssh-agent` setup; just an env var; easy to rotate | Token is broader-lived than a deploy key; must scope it tightly |
| **B. Deploy key (SSH)** — tighter security | Generate a keypair; add the **public** key as a *write-enabled deploy key* on the GitHub plugin repo; put the **private** key in Bitbucket as a secured SSH key; load into `ssh-agent` before the step; SSH URL used unchanged | Repo-specific, write-only to that one repo; no account-wide token | Slightly more CI setup (ssh-agent, known_hosts for github.com) |

Both are legitimate. **A** is the least fiddly in CI; **B** is the cleaner least-privilege
posture. Never use a broad, account-wide token.

## Required change to `deploy.sh` (small, backward-compatible)

Make it env-overridable so CI can inject the remote/identity without editing the script;
local usage stays byte-identical (defaults to today's SSH URL):

- `PLUGIN_REMOTE` — allow override via env (e.g. CI sets the
  `https://x-access-token:${TOKEN}@github.com/...` form for Option A). Default: current SSH
  URL.
- Git identity — if `GIT_AUTHOR_NAME` / `GIT_AUTHOR_EMAIL` (or explicit env vars) are
  present, set `user.name`/`user.email` on the temp clone before committing. Default:
  ambient config (local behavior unchanged).
- `known_hosts` for Option B: the step preloads `github.com` host key (`ssh-keyscan`) to
  avoid an interactive prompt. (Not needed for Option A / HTTPS.)

No change to the sync/commit/push logic itself.

## Bitbucket pipeline wiring

Add a release-only step, after the Java artifacts deploy, that invokes `deploy.sh`. Sketch
(Option A / PAT shown):

```yaml
# in the master (release) pipeline, after the existing deploy-production step
- step:
    name: Deploy Vim plugin to GitHub
    script:
      - export PLUGIN_REMOTE="https://x-access-token:${GH_PLUGIN_TOKEN}@github.com/verhasi/java-composition.vim.git"
      - export GIT_AUTHOR_NAME="ci-bot"
      - export GIT_AUTHOR_EMAIL="ci@example.invalid"
      - ./editors/vim/deploy.sh
```

- `GH_PLUGIN_TOKEN` is a **secured** Bitbucket repository variable (the fine-grained PAT).
- Gate it to run only on the release branch (`master`), consistent with the existing
  release-only steps.
- For Option B instead: configure the SSH key in Bitbucket, `ssh-keyscan github.com >>
  ~/.ssh/known_hosts`, and leave `PLUGIN_REMOTE` at its SSH default.

## Verification

- **Dry run first**: run the step with `deploy.sh --dry-run` in a throwaway pipeline to
  confirm auth works and the diff is as expected, before enabling the real push.
- Confirm idempotency: a second release with no `editors/vim/` change → "No changes to
  deploy" (no empty commit).
- Confirm least privilege: the credential cannot push anywhere except the plugin repo.

## Out of scope / open decisions

- **Credential choice (A vs B)** — decide based on tolerance for token storage vs. ssh-agent
  setup. Recommendation: A (PAT) for simplicity, B (deploy key) if tighter scope is
  preferred.
- **Release tagging of the plugin** — currently a content deploy (no version tags on the
  plugin repo). If tagged plugin versions are wanted later, add tagging to `deploy.sh`
  (e.g., mirror the release version) as a separate enhancement.
- **Who creates the credential** — an account action (generate PAT/deploy key, add to GitHub
  repo settings + Bitbucket secured variables). Cannot be done from the codebase.

## Summary

Yes — `deploy.sh` can be included in the Bitbucket release pipeline to publish the plugin
artifact during release. The only real work is auth: a least-privilege GitHub write
credential (fine-grained PAT or deploy key) stored as a secured Bitbucket variable, a CI git
identity, release-only gating, and a small env-overridable tweak to `deploy.sh`. Everything
else already works.
