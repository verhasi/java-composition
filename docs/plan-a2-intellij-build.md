# Plan: A2 — IntelliJ Plugin (Concise Method Bodies) — Build

Consolidates the spike findings (`editors/intellij/SPIKE-FINDINGS.md`) into a build plan for a
real, shippable IntelliJ plugin that gives a clean editing experience for concise method
bodies (`-> expr;` and `= ref;`). Compilation is already handled by the Maven preprocessor;
this plugin is the **live-editor** layer only.

## Scope

**In scope (A2):** per-method concise forms (`->`, `=`) — including in classes that
`implements` an interface (the flagship `UnmodifiableCollection` case).
Three editor concerns, each with a spike-proven mechanism:
1. **Colour** the `->`/`=` markers (and leave the payload to normal Java coloring).
2. **Suppress** the false local squiggles (parse errors + field-as-type "cannot resolve").
3. **Complete** the class so `implements` doesn't report "must implement" for concise methods.

**Out of scope (→ Phase 3):** wildcard forms (`Map::* = store::*`) — their fragmented parse
cascades and corrupts class structure; deferred.

## Build discipline

The spike was a **proof of concept**; its code is a **prototype** and is **not** the starting
point. Prototypes are preserved as a museum exhibit (`editors/intellij-spike-museum/`) and
never promoted to production. A2 is built **fresh from the findings below** — the *experience*
carries over, the *code* does not. New production plugin lives in a clean `editors/intellij/`.

## Proven architecture (evidence from the spike)

| Concern | Mechanism | Spike status |
|---|---|---|
| Injection of payload | `MultiHostInjector` | ❌ NOT viable (payload in non-host `PsiErrorElement`) — abandoned |
| Marker colour | `Annotator` + `textAttributes(KEYWORD)` | ✅ renders inside error elements |
| Parse squiggles | `HighlightInfoFilter` | ✅ suppressed (`Unexpected token`, `'{' expected`, `Identifier expected`) |
| Field-as-type "cannot resolve" | `HighlightInfoFilter` | ✅ suppressed (stray `PsiTypeElement` for a field) |
| Class "must implement" | `PsiAugmentProvider` (synthesize methods) | ✅ satisfies the check; gutter markers appear |

Precedent: Lombok uses `HighlightInfoFilter` + `PsiAugmentProvider` for the same class of
false-positives (strategy only; no code reused).

## Known gotchas the build MUST handle (from the spike)

1. **`PsiAugmentProvider` re-entrancy.** Calling `psiClass.getMethods()` inside `getAugments()`
   re-invokes augment providers → recursion (spike fired 16,182×). MUST detect
   already-declared methods **without** `getMethods()` — inspect the class's own physical
   declarations (AST/stub) non-recursively, and/or use Lombok-style recursion guards +
   dumb-aware + caching (`CachedValuesManager`).
2. **Residual trailing parse errors.** `'Identifier expected'` on the trailing `;` and
   `'{' or ';' expected'` on trailing whitespace after a concise body are NOT yet matched by
   `isInConciseContext`. Extend the matcher to the `;`/whitespace immediately following a
   concise marker construct.
3. **Cosmetic leftover underline.** The red underline may persist visually even where no error
   is attached (hover shows Javadoc, not an error). Investigate whether it clears on
   `DaemonCodeAnalyzer.restart()` or is a stale-highlight artifact; ensure the final UX is
   clean.
4. **Signature extraction.** The spike hardcoded synthetic methods. The real augment must read
   the concise method's actual signature (name, return type, params, type params, throws) from
   the recovered PSI (`PsiMethod` header is intact; body is the sibling error) to synthesize an
   accurate `LightMethodBuilder`.
5. **`=` form fragmentation.** The `= ref;` form parses worse than `->` (scattered nodes);
   ensure colour + suppression cover its scattered tokens, and augment still recognizes it as a
   declared method.
6. **Scope to method-body position.** All matching must be anchored to a concise construct in
   method-body position (after a method's `)`), never touching ordinary `=`/`*`/`::` in valid
   code. Spike verified no over-suppression on normal code — keep that invariant under tests.

## Build tasks

> Written fresh under a clean `editors/intellij/` (new Gradle project). The museum spike is a
> reference for *behaviour and gotchas only* — no code is copied from it.

### A2.0 — Fresh project scaffold
- New `editors/intellij/` Gradle IntelliJ Platform Plugin project (IDEA + `com.intellij.java`),
  clean `plugin.xml`. (The spike's Gradle setup is a known-good reference for versions.)

### A2.1 — Annotator (marker colouring)
- Implement an `Annotator` (language `JAVA`) that colours `->`/`=` markers with
  `DefaultLanguageHighlighterColors.KEYWORD`, scoped strictly to method-body position (marker
  after a method's parameter list). Optionally colour the `::` of the `=` form.
- No debug logging in production; add an opt-in trace switch if useful.

### A2.2 — HighlightInfoFilter (suppress false squiggles)
- Implement a `HighlightInfoFilter` that suppresses, at concise constructs only:
  parse errors on markers, field-as-type "cannot resolve", AND the trailing `;`/whitespace
  errors (gotcha #2).
- Matching must be recursion-safe, position-anchored, and never suppress errors in normal code
  (enforced by tests).

### A2.3 — PsiAugmentProvider (satisfy "must implement") — the real work
- Synthesize `PsiMethod`s (via `LightMethodBuilder`) for concise methods so `implements` is
  satisfied and duplicates are avoided.
- **Recursion-safe** already-declared detection WITHOUT `getMethods()` (gotcha #1): inspect the
  class's own physical declarations (AST/stub) / use recursion guards + `CachedValuesManager`,
  dumb-aware.
- **Extract real signatures** from the recovered PSI (gotcha #4) — the concise `PsiMethod`
  header is intact; read name/return/params/type-params/throws.

### A2.4 — Tests (headless regression coverage)
- `LightJavaCodeInsightFixtureTestCase` + `checkHighlighting`: concise file has NO error
  highlights (markers/payload/field-as-type/trailing); genuinely-broken file STILL errors
  (no over-suppression); interface-implementing concise class has no "must implement".
- Augment provider: no recursion, no duplicate-method errors, correct signatures.

### A2.5 — Packaging & distribution
- `plugin.xml` metadata (`since-build`, no debug/PoC EPs); build ZIP via Gradle IntelliJ
  Platform Plugin; distribute as a GitHub release ZIP (install-from-disk; Marketplace later).
- Document `build-helper-maven-plugin` `add-source` on `generate-sources` for symbol resolution
  in real builds.

### A2.6 — Retire the museum (optional)
- Once A2 ships and is stable, decide whether to keep `editors/intellij-spike-museum/` as a
  reference exhibit or delete it. Default: keep until A2 is proven in the wild, then reassess.

## Verification bar
- Concise `->`/`=` file (incl. an interface-implementing class): coloured markers, NO false
  squiggles anywhere (including method tail), NO class-level "must implement".
- Genuinely-broken code STILL errors (no over-suppression) — automated.
- Augment provider: no re-entrancy blow-up, no duplicate-method errors, correct signatures.
- Headless tests green; ZIP installs and works in a clean IDEA.

## Honest effort note
Built fresh (no prototype reuse). A2.1 (annotator) and A2.2 (info-filter) are small and
well-understood. **A2.3 (augment provider) is the real component** — recursion-safe
already-declared detection + signature extraction from recovered PSI is non-trivial and
version-sensitive. A2.4 (headless `checkHighlighting` tests) is valuable and achievable.
Overall A2 is a **real plugin project**, but every mechanism is spike-proven, so it is
**build-not-research** from here. Wildcard support stays deferred to Phase 3.
