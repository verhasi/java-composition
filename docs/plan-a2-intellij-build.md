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

> **Observed scope boundary (WildcardProbe in sandbox):** opening a wildcard-only file behaves
> correctly-but-unhelpfully — the augment provider finds NO bodyless concise `PsiMethod`
> headers (wildcards aren't `PsiMethod`s, just fragmented error nodes), so it augments nothing,
> and IntelliJ's standard "must implement …" errors all remain. A method declared in multiple
> implemented interfaces (e.g. `isEmpty()` in both `Map` and `List`) is listed many times
> (per-interface × the double-emission quirk). No crash, no recursion (log: 0 StackOverflow,
> our classes 3× benign; the SEVEREs are platform plugin-unload/Gradle artifacts). Confirms the
> A2 scope boundary is safe: wildcard files are simply not helped, not broken.

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
   **VERIFIED FIXED (A2.3):** the `getChildren()`-based provider is mentioned only **3× (all
   benign INFO)** in a full sandbox session vs. the prototype's 16,182× — no recursion, no
   StackOverflow. (A `SEVERE` slow-op in the log is a platform-side plugin-UNLOAD artifact
   from rebuild/reload — `DynamicPlugins`/`InjectedLanguageManager` — not our code.)
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

### A2.0 — Fresh project scaffold ✅ DONE
- New `editors/intellij/` Gradle IntelliJ Platform Plugin project (IDEA Community 2024.3 +
  `com.intellij.java`), clean production `plugin.xml` (real metadata, NO extension points yet),
  test framework wired (`testFramework(Platform)` + JUnit for `LightJavaCodeInsightFixtureTestCase`).
  Gradle 9.4.1 / plugin 2.18.1 / JDK 21. `buildPlugin` SUCCESSFUL (empty-but-valid ZIP);
  `compileTestJava` resolves. No prototype code copied.

### A2.1 — Annotator (marker colouring) ✅ DONE
- `ConciseMarkerAnnotator` (language `JAVA`, registered): colours `ARROW`/`EQ`/`DOUBLE_COLON`
  markers with `KEYWORD`, scoped to method-body position via a positional anchor (marker inside
  a `PsiErrorElement` whose preceding non-whitespace sibling — walking past stray error/type
  nodes for the `=` form — is a `PsiMethod`). No debug logging.
- Test framework fix: `LightJavaCodeInsightFixtureTestCase` needs
  `TestFrameworkType.Plugin.Java` (not `Platform`).
- `ConciseMarkerAnnotatorTest` (headless): verifies the annotator does not flag normal
  lambda `->` / assignment `=` (self-contained, no JDK types — light fixture has no full JDK).
  PASSES. Note: silent-INFO colour itself is not headlessly assertable → colour confirmed
  manually in sandbox (museum spike).

### A2.2 — HighlightInfoFilter (suppress false squiggles) ✅ DONE
- `ConciseHighlightInfoFilter` (registered `daemon.highlightInfoFilter`): suppresses
  error/warning highlights ONLY within a concise construct in method-body position — covers
  marker parse errors, field-as-type "cannot resolve", and trailing `;`/whitespace errors.
  Position-anchored (walks stray/error siblings back to a `PsiMethod`); leaves INFO/silent
  highlights (our colouring) and all normal-code errors alone. **Does NOT call
  `getMethods()`** (avoids the augment re-entrancy trap) — inspects local sibling structure only.
- `ConciseHighlightInfoFilterTest` (headless): asserts a genuine syntax error in normal code
  is STILL reported (no over-suppression) and normal code stays clean. PASSES. (Suppression of
  the concise squiggles themselves is confirmed visually in sandbox — brittle to assert via
  `checkHighlighting` on shifting parser errors.)

### A2.3 — PsiAugmentProvider (satisfy "must implement") ✅ DONE (core)
- `ConciseAugmentProvider`: iterates the class's PHYSICAL children (`getChildren()`, NOT
  `getMethods()` → recursion-safe) and, for each concrete bodyless (concise) `PsiMethod`,
  synthesizes a body-bearing twin (`LightMethodBuilder`) copying name/return/modifiers/params
  so the class satisfies `implements`. Navigation points to the user's concise method.
- The synthesized twin causes a FALSE `'x() is already defined'` duplicate (user's concise
  header + our twin); `ConciseHighlightInfoFilter` suppresses that ONLY on concise (bodyless)
  methods — genuine duplicates between real-bodied methods remain reported (tested).
- Tests (headless, discriminating): `FullImpl` (all methods concise) → CLEAN;
  `PartialImpl` (one method omitted) → STILL shows "must implement value()" (no
  over-augmentation). Both PASS.
- **Known cosmetic quirk (accepted):** IntelliJ lists the "must implement" error TWICE on the
  partial class (double-emission on the class-declaration range); the Implement-Methods popup
  shows it once, correctly. Predates the augment work; not a functional issue.
- **Signature fidelity ✅ DONE:** the twin now also copies type parameters
  (`addTypeParameter`), thrown exceptions (`addException` from `getThrowsList`), and preserves
  varargs (via the parameter's own `PsiType`). Edge-case test covers generic/throws/void/
  varargs concise implementations of an interface — all accepted.

### A2.4 — Tests (headless regression coverage) ✅ DONE
- 7 headless tests across 3 classes (`LightJavaCodeInsightFixtureTestCase`):
  - annotator does not flag normal `->`/`=`;
  - filter keeps genuine syntax errors, keeps genuine duplicate methods, leaves normal code
    clean (no over-suppression);
  - augment accepts a full concise implementation, still errors on a partial one, and accepts
    generic/throws/void/varargs signatures.
- Colour rendering + concise-squiggle suppression themselves are confirmed visually in the
  sandbox (silent-INFO / shifting-parser-errors are brittle to assert headlessly).

### A2.5 — Packaging & distribution ✅ DONE
- `plugin.xml`: real metadata + change-notes; three production EPs (annotator, info-filter,
  augment provider); no debug/PoC EPs. Gradle `ideaVersion`: `sinceBuild=243`, `untilBuild`
  open (verified in the packaged plugin.xml: `<idea-version since-build="243" />`, no cap).
- ZIP builds via `./gradlew buildPlugin`. README documents install-from-disk (with the
  required restart — the plugin is not dynamically unloadable due to the augment provider),
  the `build-helper-maven-plugin` `add-source` binding for symbol resolution, scope, and the
  known cosmetic quirks. GitHub release ZIP is the distribution channel (Marketplace later).

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
