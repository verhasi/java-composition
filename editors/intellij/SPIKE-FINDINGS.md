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
injection is NOT viable as documented.** Abandon the `MultiHostInjector` approach.

**A2 architecture (evidence-based): `Annotator` + `HighlightErrorFilter`.**

Why this is actually good news, not just a fallback:
- For the **`->` form**, the parser has *already fully parsed the payload* into real Java PSI
  **inside** the error element. An `Annotator` can descend into that `PsiErrorElement`, color
  the `ARROW` token as an operator, and the already-parsed `c.size()` reference/call nodes get
  proper coloring — **no injection needed**; the parser did the work.
- **`HighlightErrorFilter`** has a precise, observable match: suppress a `PsiErrorElement`
  whose first child is an `ARROW` (or `EQ`) token in method-body position (immediately after a
  `PsiMethod`'s parameter list / its trailing empty error element).

Honest caveats:
- The **`=` form is fragmented**, not a single node. Highlighting it will require walking a
  run of sibling error/stray nodes (`EQ`, `Math` type, `::` error, `max` type). Expect the
  `=` form to get only *partial* / best-effort highlighting, and error suppression there to be
  trickier (multiple error elements to match). The `->` form is the clean win.
- Both are heuristics over recovered PSI, so they may shift across IDE versions — acceptable
  for an editor-highlighting aid, but worth a note.

## Next steps (A2 build, post-spike)

1. Replace the spike `ConcisePayloadInjector` with an `Annotator` that:
   - colors the `ARROW`/`EQ` marker token as `OPERATION_SIGN`;
   - for the `->` form, colors the already-parsed payload nodes (or leaves them to default
     Java coloring if they already resolve).
2. Tighten `ConciseErrorFilter` to match the observed shapes precisely (first-child
   `ARROW`/`EQ`, method-body position), for both the empty trailing error and the payload
   error element.
3. Handle the `=` form's fragmentation explicitly (match the sibling run); accept partial
   coverage if full is infeasible.
4. Keep injector code removed from `plugin.xml` (or leave disabled) — it is not the path.
