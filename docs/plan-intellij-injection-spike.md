# Plan: IntelliJ Support — Language-Injection Spike (Task A2, phase 1)

## Goal of the spike

Decide — with **evidence, not prediction** — whether IntelliJ **language injection** can give
concise method bodies proper Java highlighting (and later completion) by injecting the
payload as a Java expression. This is a time-boxed investigation before any full-plugin
investment. Ship value only after the spike tells us which architecture is viable.

Confirmed decisions (from discussion):
- Build with **Gradle** (Gradle IntelliJ Plugin), isolated under `editors/intellij/`.
- Distribute later as a **GitHub ZIP** (local install-from-disk during development).
- **Agile**: this spike first; review its result before further investment.
- **Scope A2 = highlighting + error handling only.** Preview popup, completion, refactoring
  are later phases.

## Background (verified against JetBrains docs, Dec 2025)

IntelliJ parses every Java file into a **PSI tree** using its own hardcoded Java parser. Our
`int size() -> c.size();` is invalid Java to that parser, so it emits a `PsiErrorElement`
("`{` expected") — that is what paints the red squiggle, and the payload `c.size()` is not
understood as an expression.

**Language injection** lets a plugin declare that a region of host text should be parsed as
another language, supplying **prefix/suffix** so the fragment becomes valid. The docs' XML→Java
`MultiHostInjector` example is directly analogous:

```java
registrar.startInjecting(JavaLanguage.INSTANCE);
registrar.addPlace("class MyDsl { void ", "() {", context, rangeForMethodName(context));
registrar.addPlace(null, "}}", context, rangeForBody(context));
registrar.doneInjecting();
```

For us, the payload could be wrapped like prefix `class X{Object m(){return ` + payload +
suffix `;}}` to parse it as a real Java expression → full semantic highlighting/completion.

**The hard constraint (the spike's whole question):** the injection host **must implement
`PsiLanguageInjectionHost`** (every doc example uses `PsiLiteralExpression`, `XmlText`, …).
Our payload sits where the Java parser **broke**, likely inside a `PsiErrorElement`, which is
**not** a `PsiLanguageInjectionHost`. You cannot inject into an arbitrary error element.

## The single question the spike must answer

> On a real concise `.java` file, **what PSI element holds the payload** (`c.size()` /
> `Math::max`), and **does it implement `PsiLanguageInjectionHost`** (or can we make a host)?

That observation decides the architecture.

## Spike steps

1. **Scaffold** a minimal Gradle IntelliJ plugin under `editors/intellij/` (Gradle IntelliJ
   Plugin; target a current stable IDEA; Java/Kotlin — Kotlin is the IntelliJ norm but Java
   is fine to match the project).
2. **Observe the PSI.** Open `sample-concise.java` in a sandbox IDE (`runIde`) and use
   **PsiViewer** (or a tiny action dumping `PsiFile` structure) to record exactly which PSI
   nodes the parser produces for each concise line — specifically the node type holding the
   payload and the shape/position of the `PsiErrorElement`(s).
3. **Classify the host.** Determine whether the payload node (or a stable ancestor) is a
   `PsiLanguageInjectionHost`.
4. **Attempt injection** with a `MultiHostInjector` (verified EP: `com.intellij.multiHostInjector`),
   wrapping the payload with a `return …;` prefix/suffix into `JavaLanguage`. Observe whether
   the payload gets real Java highlighting/completion inside the injected fragment.
5. **Attempt suppression** with `HighlightErrorFilter` (EP `com.intellij.highlightErrorFilter`)
   to hide the `'{' expected` squiggle for the concise case, and note how robust/position-
   dependent it is.

## Decision matrix (spike outcomes)

| Observation | Meaning | Next step |
|---|---|---|
| Payload node **is** an injection host (or a stable host exists) | Clean path viable | Design A2 around injection (payload) + error-filter (squiggle) |
| Payload is in a **non-host `PsiErrorElement`** | Raw injection can't attach | Fall back to `Annotator` (lexical coloring) + `HighlightErrorFilter`; injection deferred/abandoned. Note: a fully clean solution might require our own PSI/parser for `.java` — a much larger lift, explicitly out of the spike |
| Injection attaches but highlighting/errors misbehave | Partially viable | Weigh injection-with-caveats vs. annotator approach |

## Deliverables of the spike (not a finished plugin)

- `editors/intellij/` Gradle project that builds and launches a sandbox IDE.
- A short **findings note** (`editors/intellij/SPIKE-FINDINGS.md`) recording: the observed PSI
  for concise bodies, whether an injection host exists, and whether injection + suppression
  worked — with a recommendation for the A2 architecture.
- No distribution, no Marketplace, no completion/preview/refactoring.

## Explicitly out of scope for the spike

- Full highlighting polish, completion, preview popup, refactoring (later phases).
- Marketplace publishing / ZIP distribution wiring.
- Defining a custom PSI/parser for concise Java (only *flagged* if the spike shows injection
  is impossible; not attempted here).

## Verification bar

The spike "succeeds" when we can state, from observed sandbox behavior (PsiViewer output +
screenshots/notes), **which architecture A2 will use and why** — not when highlighting is
perfect. A negative result (injection not viable) is a valid, valuable outcome that redirects
us to the annotator approach without wasted full-build investment.
