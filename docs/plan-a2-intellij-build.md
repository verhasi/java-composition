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

### A2.1 — Promote & harden the Annotator
- Rename spike `ConciseMarkerAnnotator` → real annotator; remove debug logging.
- Colour `->`/`=` markers with `DefaultLanguageHighlighterColors.KEYWORD` (theme-aware).
- Scope strictly to method-body position (marker after a method's parameter list).
- Optionally colour the `::` in the `=` form.

### A2.2 — Promote & harden the HighlightInfoFilter
- Rename spike `ConciseHighlightInfoFilter`; remove debug logging (keep an opt-in trace flag).
- Suppress: parse errors on markers, field-as-type "cannot resolve", AND the trailing
  `;`/whitespace errors (gotcha #2).
- Recursion-safe, position-anchored matching; must not suppress errors in normal code.

### A2.3 — Build the PsiAugmentProvider (the real work)
- Recursion-safe detection of already-declared methods (gotcha #1).
- Extract concise method signatures from the recovered PSI (gotcha #4) and synthesize matching
  `LightMethodBuilder` methods so `implements` is satisfied.
- Cache results (`CachedValuesManager`) keyed on the file; dumb-aware.
- Do NOT duplicate user-declared (incl. concise) methods.

### A2.4 — Remove spike scaffolding
- Delete `MultiHostInjector` (dead), `DumpPsiAction` (spike instrument), spike sample probes
  (or move to test fixtures).

### A2.5 — Tests (headless, real regression coverage)
- `LightJavaCodeInsightFixtureTestCase` + `checkHighlighting`: assert that a concise file has
  NO error highlights (markers/payload/field-as-type/trailing), that a genuinely-broken file
  STILL errors (no over-suppression), and that an interface-implementing concise class has no
  "must implement". These run headless (unlike `runIde`), giving Vim-add-on-level coverage.
- A test that the augment provider does not recurse/duplicate.

### A2.6 — Packaging & distribution
- `plugin.xml` metadata (name, description, `since-build`, no debug EPs).
- Build ZIP via Gradle IntelliJ Platform Plugin (already set up).
- Distribute as a GitHub release ZIP (per decision); document install-from-disk. Marketplace
  deferred.
- Document `build-helper-maven-plugin` `add-source` on `generate-sources` so IntelliJ resolves
  the preprocessed classes in the actual build.

## Verification bar
- Concise `->`/`=` file (incl. an interface-implementing class): coloured markers, NO false
  squiggles anywhere (including method tail), NO class-level "must implement".
- Genuinely-broken code STILL errors (no over-suppression) — automated.
- Augment provider: no re-entrancy blow-up, no duplicate-method errors, correct signatures.
- Headless tests green; ZIP installs and works in a clean IDEA.

## Honest effort note
A2.1/A2.2 are small (spike code, mostly done). **A2.3 is the real component** — recursion-safe
detection + signature extraction from recovered PSI is non-trivial and version-sensitive.
A2.5 (headless highlighting tests) is valuable and achievable. Overall A2 is a **real plugin
project**, not a quick add-on — but every mechanism is now spike-proven, so it is
build-not-research from here. Wildcard support stays deferred to Phase 3.
