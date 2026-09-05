# 🏛️ Museum — IntelliJ Support Spike (Proof of Concept)

**This directory is a preserved proof-of-concept. Its code is a PROTOTYPE and must NEVER be
used, promoted, or shipped in production.**

A spike exists to produce *knowledge*, not code. The knowledge is captured in
[`SPIKE-FINDINGS.md`](./SPIKE-FINDINGS.md). The code here was a means to those answers — full
of hardcoded values, debug logging, a known re-entrancy bug, dead ends (the injector), and
throwaway instruments (PSI dumper). It is kept only as a **reference exhibit** so the
observations can be re-run or re-read; the real plugin is built fresh from the findings, per
[`docs/plan-a2-intellij-build.md`](../../docs/plan-a2-intellij-build.md).

## What was learned (see SPIKE-FINDINGS.md for detail)

- Language **injection** of the concise payload is **not viable** (payload lives in a non-host
  `PsiErrorElement`).
- Marker **colouring** via `Annotator` **works**, even inside error elements.
- False squiggles (parse errors + field-as-type "cannot resolve") are **suppressible** via
  `HighlightInfoFilter` (Lombok's strategy).
- Class-level "must implement" is **satisfiable** via `PsiAugmentProvider` synthesizing methods.
- Gotchas discovered: augment **re-entrancy** if you call `getMethods()` inside `getAugments()`;
  **residual trailing** `;`/whitespace parse errors; a cosmetic leftover underline.

## Exhibits (prototype code — do not reuse)

- `ConcisePayloadInjector` — the abandoned `MultiHostInjector` (proved injection dead).
- `ConciseMarkerAnnotator` — marker colouring PoC (incl. the "ugly yellow" visibility test).
- `ConciseHighlightInfoFilter` / `ConciseErrorFilter` — squiggle-suppression PoC.
- `ConciseAugmentProvider` — `PsiAugmentProvider` PoC (hardcoded methods; has the re-entrancy bug).
- `ConciseMarker` — marker/payload detection helper.
- `DumpPsiAction` — the PSI-dump instrument used to observe recovery.
- `sandbox-samples/` — probe `.java` files (`Sample`, `WildcardProbe`, `ImplProbe`).

## Status

Frozen. Do not build against this for release. The production IntelliJ plugin will be a new,
clean implementation under `editors/intellij/` following the A2 build plan.
