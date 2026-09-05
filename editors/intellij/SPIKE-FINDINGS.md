# IntelliJ Injection Spike — Findings

Status: **COMPLETE — observed in sandbox IDE (IDEA Community 2024.3 + Java). Decision made.**

## What was observed (PSI dump of `sample-concise.java`)

### The `->` (arrow) form parses *recoverably well*
For `public int size()            -> c.size();`, the parser produces:
```
PsiMethodImpl  'public int size()'          ← method header parses fine (name, (), throws)
  ... PsiParameterListImpl '()' ...
  PsiErrorElementImpl range=(94,94) text=''  ← empty error right after the header
PsiErrorElementImpl range=(106,117) text='-> c.size()'
  PsiJavaToken ARROW '->'
  PsiJavaCodeReferenceElement 'c.size'       ← PAYLOAD FULLY PARSED as real Java PSI
  PsiJavaToken LPARENTH '('
  PsiJavaToken RPARENTH ')'
PsiJavaToken SEMICOLON ';'                    ← trailing ; is a clean sibling
```
Key: the whole `-> c.size()` is wrapped in ONE `PsiErrorElement`, but **inside it the
payload is fully parsed** into ordinary Java reference/call nodes. The `ARROW` token is the
error element's first child.

### The `=` (method-ref) form parses *badly / fragmented*
For `static int max(int a, int b) = Math::max;`, recovery scatters it:
```
PsiMethodImpl 'static int max(int a, int b)' + trailing empty ERROR_ELEMENT
PsiErrorElement '=' (just the EQ token)
PsiTypeElement 'Math'         ← stray, parsed as a type
PsiErrorElement '::'          ← DOUBLE_COLON token
PsiTypeElement 'max'          ← stray
PsiErrorElement '' ; SEMICOLON
```
The `=` form does NOT recover into a single clean node; it fragments into several
error/stray nodes. (Consistent with the `=` form being the harder one — it also needs the
Symbol Solver in the preprocessor.)

## Answers to the spike questions

### 1. What PSI node holds the payload? Is it a `PsiLanguageInjectionHost`?
- Arrow payload (`c.size`): inside a `PsiErrorElement` (range 106–117), as a real
  `PsiJavaCodeReferenceElement`. The error element is **NOT** a host.
- The only `[HOST]` nodes in the file are unrelated: the `null` and `5` literals and the
  line comment (`PsiLiteralExpression`, `PsiComment`). **No concise payload node is a host.**

### 2. Shape of the error
- Arrow: a `PsiErrorElement` whose **first child is an `ARROW` token**, sitting as a sibling
  right after the method's header (which itself ends with an empty `PsiErrorElement`).
- Method-ref: a `PsiErrorElement` whose first child is an `EQ` token, plus further fragmented
  error/stray nodes.

### 3. Did injection attach?
- **NO.** `ConcisePayloadInjector` logged `context is NOT a PsiLanguageInjectionHost` for
  every candidate (`PsiMethodImpl`, `PsiParameterListImpl`, `PsiCodeBlockImpl`, `PsiClassImpl`,
  …). No `INJECTING` line was ever emitted — there is no valid host at the concise position.

## Decision

Per the plan's decision matrix: **payload is in a non-host `PsiErrorElement` → language
injection is NOT viable as documented.** Abandon the `MultiHostInjector` approach. (This
half is *spiked* — the injector logged "not a host" for every candidate.)

**A2 architecture (PROPOSED, pending a second spike): `Annotator` + `HighlightErrorFilter`.**

⚠️ **HONESTY NOTE — the annotator path is NOT yet spiked.** We proved injection is *not*
viable; we have **not** proven the fallback *is*. Two things remain unverified by observation:
- whether an `Annotator` is even **invoked** on tokens that live inside a `PsiErrorElement`
  (annotators may skip error-element children);
- whether any color we add **renders**, or is **overridden/obscured** by IntelliJ's own
  error highlighting on the error element.
Until the annotator spike (below) confirms coloring actually shows, "feasible" is an
assumption, not evidence.

Why it *might* work (the observation that motivates trying):
- For the **`->` form**, the parser has *already fully parsed the payload* into real Java PSI
  **inside** the error element. IF an annotator can reach in and color, the `ARROW` token and
  the already-parsed `c.size()` nodes could be highlighted — no injection needed.
- **`HighlightErrorFilter`** has a precise, observable match: a `PsiErrorElement` whose first
  child is an `ARROW`/`EQ` token in method-body position.

Honest caveats:
- The **`=` form is fragmented**, not a single node. Highlighting it will require walking a
  run of sibling error/stray nodes (`EQ`, `Math` type, `::` error, `max` type). Expect the
  `=` form to get only *partial* / best-effort highlighting, and error suppression there to be
  trickier (multiple error elements to match). The `->` form is the clean win.
- Both are heuristics over recovered PSI, so they may shift across IDE versions — acceptable
  for an editor-highlighting aid, but worth a note.

## Next steps (A2 build, post-spike)

### Annotator spike (RUN THIS NEXT — turns the assumption into evidence)
`ConciseMarkerAnnotator` (registered `<annotator language="JAVA">`) colors the marker tokens
`ARROW` / `DOUBLE_COLON` / `ASTERISK` (and method-body `EQ`) with `OPERATION_SIGN`, and logs
every hit noting whether the token is inside a `PsiErrorElement`.

**Run:** `./gradlew runIde`, open `sandbox-samples/Sample.java` (and `WildcardProbe.java`).

**Observe (the two unverified questions):**
1. **Invocation** — does `idea.log` contain `[spike-annotator] coloring marker …
   inErrorElement=true` lines? If yes, annotators DO fire on error-element children.
2. **Rendering** — do the `->` / `=` / `::` / `*` markers actually appear **colored** in the
   editor, or does the red error highlighting override them? (Screenshot.)

**Record the result here:**
- Annotator invoked on error-element children? **YES** — 456 hits, ALL `inErrorElement=true`.
  Confirmed: annotators DO fire on tokens nested in `PsiErrorElement`s.
- Color visibly renders on the markers? **NO** — observed in sandbox: red error squiggles
  ("unexpected token", "unknown class") dominate; the requested `OPERATION_SIGN` color does
  not show. Error highlighting overrides the annotator's silent color.

### Evidence-based outcome (observed, not assumed)

The annotator + filter are **coupled, not independent**: coloring cannot show while the error
highlighting is present. AND the spike surfaced a THIRD fact that reshapes A2:

**There are TWO distinct red-squiggle sources, needing different (or no) handling:**
1. **Parse errors** — `PsiErrorElement` "unexpected token". `HighlightErrorFilter` *can*
   suppress these, but the current matcher (`'{' expected` in parent) does not match the
   fragmented shapes (it fired only 9× and correctly declined — parent was a comment).
   Broadening the matcher is possible.
2. **Semantic errors** — the stray `PsiTypeElement`s (`store`, `Math`, `List`, method names)
   are flagged **"unknown class"/"cannot resolve symbol"** by IntelliJ's inspection/annotation
   pass. **`HighlightErrorFilter` does NOT control these** — it only governs `PsiErrorElement`
   highlighting, not semantic "cannot resolve" annotations. These dominate the display,
   especially for the wildcard forms, and would need a heavier/blunter mechanism
   (`HighlightInfoFilter` / custom `HighlightVisitor`) to hide.

### Honest A2 outlook (revised AGAIN — key correction)

**Earlier conclusion was wrong on the decisive point.** I claimed the semantic
"cannot resolve" errors are not suppressible and that only custom PSI could fix it. The
JetBrains docs (`controlling-highlighting.html`) refute this with an almost-exact analogy to
our case:

> a tool that changes Java syntax (implicitly generated setters) → IntelliJ reports the
> usage as an unresolved symbol → suppress it with `HighlightInfoFilter`.

The platform provides **`HighlightInfoFilter`** (EP `com.intellij.daemon.highlightInfoFilter`,
single method `accept(HighlightInfo)` → return `false` to hide). Unlike `HighlightErrorFilter`
(parse errors only), **`HighlightInfoFilter` governs ALL highlighting including semantic
"cannot resolve" errors.** The cited real-world precedent is **`LombokHighlightErrorFilter`** —
Lombok suppresses exactly this class of false-positive with it.

Also, per `syntax-highlighting-and-error-highlighting.html`, text attributes **layer** (colors
compose, not simply override). So in the annotator spike the marker text may in fact have been
colored *underneath* the error squiggle — "squiggle dominates" is not the same as "no color".
This was an imprecise reading of the spike and should be re-observed.

**Revived lightweight A2 path (no custom PSI):**
1. **Color** markers/payload — `Annotator` + `newSilentAnnotation().textAttributes()` —
   CONFIRMED applied (456 hits).
2. **Suppress parse squiggles** — `HighlightErrorFilter` / `syntax-errors.html`.
3. **Suppress semantic "cannot resolve" squiggles** — `HighlightInfoFilter`, Lombok-style.

**Status: PROMISING, not yet proven.** Still to spike: (a) a `HighlightInfoFilter` that hides
only OUR false errors (matched by concise position) without hiding real ones; (b) re-observe
whether the marker text is actually colored once squiggles are gone. If both hold, A2 is
viable WITHOUT the custom-PSI lift — a materially cheaper outcome than the previous conclusion.

### HighlightInfoFilter spike (RUN THIS NEXT)
`ConciseHighlightInfoFilter` (registered `<daemon.highlightInfoFilter>`) hides ERROR/WARNING
`HighlightInfo`s whose PSI element is at/within a concise construct (marker token, inside a
marker-bearing `PsiErrorElement`, or a stray name adjacent to a marker error). Every
suppression is logged `[spike-infofilter] SUPPRESSING …`.

**Run:** `./gradlew runIde`, open `sandbox-samples/Sample.java` and `WildcardProbe.java`.

**Observe:**
1. Do the red squiggles on the concise/wildcard lines **disappear**? (The whole point.)
2. With squiggles gone, are the `-> = :: *` markers now **visibly colored** (the annotator's
   `OPERATION_SIGN`)? → answers the "was color applied under the squiggle" question.
3. **Critical safety check**: does the normal `close()` / `normal()` method still show real
   errors if you introduce one (e.g. call an undefined method)? I.e. confirm we did NOT
   over-suppress. Check `idea.log` `[spike-infofilter] SUPPRESSING` lines are ONLY on concise
   constructs, never on normal code.

**Record:** squiggles gone? Y/N. Markers colored? Y/N. Real errors preserved? Y/N.
- All three good → **A2 viable without custom PSI** → build the real A2.
- Squiggles gone but no color → color truly overridden → revisit annotator approach.
- Over-suppresses real errors → tighten the concise-context matcher (position-based).

### Observed run #1 (HighlightInfoFilter + annotator) — PARTIAL

Sandbox observation + log (`[spike-infofilter]` = 193 suppressions, `[spike-annotator]` = 175):

- ✅ **Parse-error squiggles SUPPRESSED and it works** — the red underwaves on the marker
  punctuation (`:: = * [ ]`) were drawn, then **vanished after ~1s** (parse highlight paints
  first, then the daemon pass runs our filter and removes them). 193 suppressions logged:
  `'Unexpected token'`, `'Identifier expected'`, `'{' or ';' expected`. **This is the key
  win** — error suppression via `HighlightInfoFilter` is CONFIRMED working.
- ❌ **Semantic "cannot resolve" on MISPARSED-AS-TYPE names still red** — CORRECTION: it is
  NOT all names. The parser parses the RHS of `=` as `Type::member`, so each name becomes a
  stray `PsiTypeElement`. A name that is coincidentally a real **class** (`Math`) resolves →
  **no error**. A name that is actually a **field/variable** (`store`, `items`) does NOT
  resolve as a type → **"cannot resolve symbol" error**. So only the field-as-type cases are
  red. The log shows suppressions only on punctuation, none on these stray type nodes — a
  matcher GAP (the field-name `PsiTypeElement`s sit between marker error elements and weren't
  matched). Fixable by targeting stray type/reference nodes adjacent to concise markers.
- ❌ **Markers NOT visibly colored** (`->` / `=`) — even with squiggles gone, no color showed.
  So the earlier "overridden by squiggle" hypothesis is WRONG; the color simply isn't
  visible. Two candidates: (a) `newSilentAnnotation` on tokens inside a `PsiErrorElement` may
  not paint; (b) `OPERATION_SIGN` ≈ default foreground in the theme → invisible.
  **Run #2 change:** annotator now uses `enforcedTextAttributes` with a blatant YELLOW
  background + RED bold text to disambiguate "not applied" from "applied but same color".

### Run #2 — to observe
Re-run `runIde`. Look at a `->` / `=` marker:
- **Yellow highlight visible?** → annotations DO paint inside error elements; the earlier
  problem was just an invisible color. Then the real annotator picks a proper theme color.
- **Still nothing?** → `newSilentAnnotation`/annotations do not paint inside `PsiErrorElement`
  children; coloring the payload needs a different route (color the stray parsed nodes that
  ARE outside the error element, or reconsider).
Record the result and, separately, note that the semantic-name suppression matcher needs
broadening regardless.

### Superseded (kept for history)
The prior "two error sources, semantic ones unsuppressable, needs custom PSI" conclusion is
superseded by the `HighlightInfoFilter` finding above. The two-error-source *observation*
stands; the claim that the semantic source is unsuppressable does not.

### After the annotator spike confirms viability
1. Turn `ConciseMarkerAnnotator` into the real A2 annotator (scope to method-body position,
   color marker + `->` payload).
2. Tighten `ConciseErrorFilter` to the observed shapes (first-child `ARROW`/`EQ`).
3. Handle the `=` form's fragmentation as best-effort.
4. Remove/disable the `MultiHostInjector` (not the path).

---

## Forward probe — Phase 3 wildcard delegation (future intelligence)

`sandbox-samples/WildcardProbe.java` probes how IntelliJ's Java parser recovers on the
**Phase 3** class-level composition syntax (`docs/requirements-phase3.md`):
`[Type(s)]::[method(s)] = target::[method(s)]`, including `*`, bracket groups, type params,
method renames, and auto-discover targets. These are **class-body-level** declarations, a
different parser position than the per-method `->`/`=` bodies — so recovery may look very
different.

**Why probe now:** while the PSI-dump instrument exists, capturing how the parser breaks on
these forms is cheap and tells us, ahead of time, whether class-body-level highlighting/error
suppression will be feasible and what PSI shapes we'd key off.

**How to record (run `runIde`, open the file, Tools | Dump PSI, read idea.log):** for each
family (A–K in the file), note the recovered node shape — e.g. does `Map::* = store::*;`
land in one `PsiErrorElement`, or fragment like the per-method `=` did? Does any sub-part
parse into usable PSI? Append a short table here per family. (Left unfilled until observed;
this is intelligence-gathering for Phase 3, not part of the A2 build.)

### Observed results (sandbox IDEA Community 2024.3)

**Overall pattern:** unlike the per-method `->` form (which recovers into ONE
`PsiErrorElement` with a fully-parsed payload), every class-body-level wildcard declaration
**fragments** into a scattered sibling run:
- type-position names (`Map`, `List`, `store`, `items`, method names) → stray
  `PsiTypeElement` + `PsiJavaCodeReferenceElement` (they DO resolve as references);
- punctuation `:: * [ ] , =` → small `PsiErrorElement`s, oddly grouped
  (e.g. `'::* ='`, `']::* ='`, `'::['`);
- trailing `;` → clean `SEMICOLON` sibling;
- empty `PsiErrorElement`s (range x,x) at boundaries.
- **No wildcard node is a `PsiLanguageInjectionHost`** → injection dead for these too.

| Family | Form | Recovery shape |
|---|---|---|
| A | `size() = store::size` | per-method `=`: header method + trailing empty error; then `=` error, `store` type, `::` error, `size` type — fragmented (matches earlier finding) |
| B | `*::* = delegate::*` | error `'*::* ='` (ASTERISK/DBL_COLON/ASTERISK/EQ) → `delegate` type → error `'::*'` → `;` |
| C | `Map::* = store::*` | `Map` type → error `'::* ='` → `store` type → error `'::*'` → `;` (clean, repeatable) |
| D | `[List, Closeable]::* = items::*` | error `'['` → `List` type → error `','` → `Closeable` type → error `']::* ='` → `items` type → error `'::*'` → `;` |
| E | `[List<V>, Map<K,V>]::* = store::*` | same as D; **type parameters inside the brackets fully parse** (`List<V>`, `Map<K, V>`) |
| F | `List::size = items::size` | `List` type → error `'::'` → `size` type → empty error → error `'='` → `items` type → error `'::'` → `size` type → `;` |
| G | `List::[size, isEmpty] = items::*` | `List` type → error `'::['` → `size` type → error `','` → `isEmpty` type → error `'] ='` → `items` type → error `'::*'` → `;` |
| H | `List::get = items::getOrDefault` | like F; names (`get`, `getOrDefault`) as stray types |
| I | `Map::* = *::*` | `Map` type → **entire RHS `'::* = *::*'` collapses into ONE error element** (most compact) → `;` |
| J | `Map::* = [store, resource]::*` | `Map` type → error `'::* = ['` → `store` type → error `','` → `resource` type → error `']::*'` → `;` |
| K | `List::[get, set] = items::[get, set]` | fully fragmented: types for every name, errors for every `:: [ ] , =` |
| (normal) | `void close() { ... }` | **intact** `PsiMethod` + `PsiCodeBlock` — untouched ✓ |

### Phase 3 IDE-support implications
1. **Injection: not viable** (no host), consistent with the per-method `=`.
2. **Highlighting via `Annotator`: UNVERIFIED** — marker tokens (`:: * [ ] =`) sit in
   identifiable `PsiErrorElement`s and names are stray `PsiTypeElement`s that already resolve,
   so coloring *looks* reachable — but whether an annotator is invoked on error-element
   children and whether the color renders (vs. being overridden by error highlighting) is
   **not spiked**. Same open question as the A2 annotator spike.
3. **Error suppression: hard** — each line yields MULTIPLE, oddly-grouped error elements
   (not one), so suppressing precisely without hiding real errors is fragile. Likely the
   toughest part of any future Phase 3 IDE support.
4. **Consistency is the asset** — the decomposition (type-position types + punctuation errors
   + name types + `;`) is reliable enough that a *recognizer* could pattern-match the sibling
   run. Useful intelligence for a future preprocessor front-end, not only the IDE.
5. **Type parameters parse** even inside bracket groups (Family E) — good news for any
   type-aware processing.

**Conclusion for now:** Phase 3 class-body syntax is parseable-enough to *recognize* (stable
fragmentation) but *not* cleanly highlightable/suppressible in IntelliJ without significant
sibling-run heuristics. Defer Phase 3 IDE support; this dump is the reference when it's taken
up. The per-method `->`/`=` forms (A2) remain the near-term target.

