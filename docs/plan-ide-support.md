# Plan: IDE / Editor Support for Concise Method Bodies

## Goal

Make concise method bodies (JEP 8209434: `-> expr;` and `= ref;`) a first-class editing
experience across the editors Java developers actually use — **without** re-implementing a
Java parser or a full LSP server. The Maven preprocessor already handles compilation
(`generate-sources` phase); this plan covers the **live editing** layer only: error
suppression, highlighting, hover/expansion, completion, and refactoring.

## Coverage strategy — 3 implementations cover >95% of Java developers

| Target ecosystem | Editors covered | Artifact | Effort |
|---|---|---|---|
| JetBrains | IntelliJ IDEA, Android Studio | IntelliJ plugin (PSI-based) | Medium |
| LSP ecosystem | VS Code, Cursor, Fleet, Helix, Sublime, **Eclipse (via LSP4E)**, Neovim+LSP | TextMate/Tree-sitter grammar + LSP proxy | Medium |
| Vim/Neovim (CLI) | Vim, Neovim terminal, SSH edits | `.vim` syntax file + Tree-sitter query | Very low |

Key insight: **Eclipse does not need a separate native RCP/OSGi plugin** — modern Eclipse
speaks LSP through LSP4E, so the LSP proxy artifact covers it. That collapses the naive
"4 implementations (Vim, IntelliJ, Eclipse, LSP)" down to **3**.

### Architectural notes captured from the discussion

- **IntelliJ** uses its own PSI tree (not LSP). It flags concise bodies as `'{' expected`
  errors by default → we need `HighlightErrorFilter` to suppress, plus `Annotator` to color.
- **LSP editors** mostly run `eclipse.jdt.ls` (headless Eclipse JDT) under the hood. Two
  extension routes: (a) an **LSP proxy** that preprocesses the buffer in-memory and remaps
  offsets (client-agnostic, preferred), or (b) a server-side JDT LS OSGi plugin.
- **Tree-sitter** = incremental, error-tolerant CST parser for editors (not a formal CS
  term; a specific tool by Max Brunsfeld). It is error-tolerant, so concise tokens land in
  `(ERROR)` nodes we can still target with S-expression queries for highlighting.
- **JavaParser vs Tree-sitter**: JavaParser = semantic AST, Symbol Solver, transformation,
  batch (our preprocessor). Tree-sitter = syntactic CST, microsecond incremental, error
  tolerant (editor highlighting). Different pipeline stages; both useful here.
- The LSP proxy pattern (preprocess buffer + offset remap) is the reusable core that powers
  diagnostics, hover, completion, and code actions for the entire LSP ecosystem at once.

---

## Phase A — Baseline editing support (highlight + error suppression)

### Task A1 — Vim/Neovim syntax (quick win, do first)
- `after/syntax/java.vim`: match `)\s*(->|=)` and color the marker as `Operator`; map the
  payload to standard expression scopes.
- Neovim Tree-sitter query (`queries/java/highlights.scm`) targeting the concise marker.
- **Effort**: <1 hour. **Verify**: open a concise `.java` in Vim/Neovim, markers colored,
  file not visually broken.

### Task A2 — IntelliJ plugin: error suppression + highlighting
- `HighlightErrorFilter`: suppress the `'{' expected` `PsiErrorElement` when `->`/`=`
  immediately follows a method's `PsiParameterList`.
- `Annotator`: color `->`/`=` as `OPERATION_SIGN`; color the RHS as a proper expression.
- Bind generated sources: document `build-helper-maven-plugin` `add-source` on
  `generate-sources` pointing at the preprocessor output so IntelliJ resolves symbols.
- **Verify**: concise file shows no red squiggles; markers + payload colored; project
  resolves preprocessed classes after Maven import.

### Task A3 — LSP grammar injection (client-side highlighting)
- Shared TextMate injection grammar (`injectionSelector: "L:source.java"`) matching the
  concise marker, reusing standard Java scopes for theme consistency.
- Optional Tree-sitter query variant for Tree-sitter-based clients (Neovim, Helix, Zed).
- **Verify**: highlighting appears in VS Code (and one Tree-sitter client) with no LSP work.

---

## Phase B — LSP proxy (semantic support across all LSP editors + Eclipse)

### Task B0 — Shared `expandSnippet(String)` entry point (prerequisite, just-in-time)
- Extract a small reusable API from the preprocessor that expands a **single method
  snippet** (concise → standard Java) in-memory, independent of file I/O. The current
  `Preprocessor.process(Path)` is file-oriented; the LSP proxy (B2) and the IntelliJ preview
  popup (C5) both need to expand an in-editor buffer/method string instead.
- Reuse the existing pipeline (Stage 2 recognition → Stage 3 transformation) — this is a
  refactor to expose an entry point, not new expansion logic.
- **Do it just-in-time**: implement when the first consumer arrives (whichever of B2 / C5 is
  built first), not upfront. Whoever gets there first extracts it; the other reuses it.
- **Verify**: `expandSnippet(conciseMethodText)` returns the standard-Java form; existing
  `process(Path)` still passes all integration tests (refactored to route through it).

### Task B1 — LSP proxy skeleton
- Thin middleware (Node/Go/Rust) between editor client and `eclipse.jdt.ls`. Pass all
  standard traffic straight through; establish the intercept points.

### Task B2 — In-memory preprocess + offset map
- Intercept `textDocument/didOpen` / `didChange`: run the preprocessor on the buffer,
  forward valid Java to `eclipse.jdt.ls`.
- Maintain a bidirectional line/char offset map between concise source and expanded Java.

### Task B3 — Remap diagnostics, hover, semantic tokens
- Translate ranges on responses (`publishDiagnostics`, hover, `semanticTokens/full`) back to
  concise-source coordinates using the offset map.
- **Verify**: no false diagnostics on concise bodies in VS Code + Eclipse (LSP4E); hover
  lands on correct ranges.

---

## Phase C — Editor features (agreed: all EXCEPT debugging/SMAP)

> The JSR-45 SMAP debugging/source-map feature was **explicitly dropped** as too complex for
> now.

### Task C1 — Bidirectional refactoring (intention / code actions)
- **Collapse**: standard method with a single `return expr;` (or single void stmt) →
  offer "Convert to concise body" (`-> expr;` / `= ref;`).
- **Expand**: concise body → "Expand to standard body" (`{ return expr; }` or `{ expr; }`
  for void).
- IntelliJ: `PsiElementBaseIntentionAction` (both directions).
- LSP: proxy returns `textDocument/codeAction` workspace edits.
- **Verify**: round-trip collapse↔expand preserves semantics on the golden fixtures.

### Task C2 — Formatter & code-style integration
- IntelliJ `PostFormatProcessor`: normalize spacing around `->`/`=` (`) -> ` canonical);
  don't break surrounding AST on Ctrl+Alt+L.
- Maven: add a `format` goal / `spotless-maven-plugin` step so `mvn ...:format` cleans
  concise syntax in CI without an IDE.
- **Verify**: auto-format is idempotent and doesn't mangle concise declarations.

### Task C3 — Static analysis & style enforcement
- Checkstyle `AbstractCheck` on `METHOD_DEF`: suggest concise form for single-`return`
  methods.
- Complexity guardrail: warn when a concise expression is too long / deeply nested
  (ternaries) → suggest expanding.
- **Verify**: rule fires on candidates, is silent on already-concise/complex-exempt cases.

### Task C4 — Smart inlay hints
- IntelliJ `InlayHintsProvider` and LSP `textDocument/inlayHint`: show resolved return type
  / inferred info inline after the parameter list.
- **Verify**: hints render in IntelliJ and one LSP client; toggle off cleanly.

### Task C5 — Expanded-source preview popup (from the discussion)
- IntelliJ: `DocumentationProvider` (Ctrl+Q/Cmd+J) showing the in-memory expanded snippet,
  and/or a `LineMarkerProvider` gutter icon opening a read-only `EditorTextField` popup
  (full Java highlighting) via `JBPopupFactory`.
- Compute the preview **in-memory** from the active `PsiMethod` (reflects unsaved typing),
  reusing our preprocessor's expansion logic.
- **Verify**: hovering/clicking a concise method shows the correct expanded standard Java.

---

## Phase D — Completion

### Task D1 — IntelliJ completion
- `CompletionContributor`: after `->`/`=`, evaluate variants in the enclosing `PsiMethod`
  scope so parameters/fields resolve in the expression.
- `PostfixTemplate`/`CompletionProvider`: typing the marker formats the line and appends `;`.

### Task D2 — LSP proxy completion
- Intercept `textDocument/completion`: expand buffer in-memory, remap cursor into the
  `{ return …; }` position, forward to `eclipse.jdt.ls`, pass completions back.
- **Verify**: type-aware completion inside a concise body in VS Code + Neovim(LSP).

### Task D3 — Vim fallback
- Neovim+LSP inherits D2 automatically. Pure Vim: rely on built-in buffer keyword
  completion (Ctrl+N/P). No extra work beyond documenting it.

---

## Suggested ordering (value / effort)

1. **A1 Vim syntax** — trivial, immediate polish.
2. **A2 IntelliJ suppress+highlight** — biggest single user share (~72%+).
3. **A3 LSP grammar injection** — cheap client-side highlighting for the whole LSP set.
4. **B1–B3 LSP proxy** — unlocks semantic correctness (diagnostics/hover) for LSP + Eclipse.
   (Extract **B0 `expandSnippet`** just-in-time here, or at C5 — whichever comes first.)
5. **C5 preview popup** + **C1 refactoring** — high-delight, build on existing expansion logic.
6. **D1/D2 completion** — depends on the proxy (B) and IntelliJ scope work.
7. **C2 formatter, C3 static analysis, C4 inlay hints** — polish/adoption aids.

## Explicitly out of scope (for now)
- JSR-45 SMAP debugging / source maps (dropped as too complex).
- Native Eclipse RCP/OSGi plugin (covered via LSP4E + the LSP proxy).
