# Plan: Recovery-Fork + Processor Architecture

## Goal

Re-implement concise method bodies on top of a **minimal, general** JavaParser fork that
retains unparsed tokens as an `UnparsedFragment` node, plus a **`Processor`** (registered
on `ParserConfiguration`) that expands those fragments. This replaces the current
grammar-level fork. Both forks live side by side until the new path passes the existing
tests unchanged; then the old fork is deleted.

## Target Architecture

```
Source (.java with concise bodies)
   │
   ▼  new fork: standard parse; on ParseException in a method body,
   │            recovery retains the skipped tokens as an UnparsedFragment
   │            (node placed in the body slot) instead of discarding them
   ▼  ParseResult<CompilationUnit> with UnparsedFragment holes
   │
   ▼  ConciseBodyProcessor.postProcess(result, config)   ← registered on config
   │            finds UnparsedFragment nodes, re-parses the tokens as
   │            `-> expr ;` / `= MethodRef ;`, builds a standard BlockStmt,
   │            replaces the fragment (Symbol Solver for the `=` form)
   ▼  Fully-expanded standard-Java CompilationUnit
```

Two clean responsibilities:

- **New fork (`javaparser-core` only)** — *general* recovery retention. No concise-body
  knowledge. Candidate for upstream contribution.
- **Preprocessor** — all concise-specific logic, as a `Processor` driven by the parser.

## Decisions (confirmed)

1. **Fork base**: upstream `3.28.2` (latest stable).
2. **Coexistence**: new fork gets a distinct version and a **distinct relocation package**
   so both forks can be on the classpath during side-by-side testing:
   - Current fork: `3.28.2-java-composition`, shaded to `guru.mocker.internal.javaparser`.
   - New fork: `3.28.2-java-composition-recovery`, shaded to
     `guru.mocker.internal.recovery.javaparser` (distinct — no collision).
3. **Swap trigger**: the existing test suite (golden files + parsing tests) passes against
   the new fork + `ConciseBodyProcessor` **without modifying any test**. Then delete the
   old fork.
4. **Expansion is a `Processor`**: external code, parser-driven, registered via
   `ParserConfiguration.getProcessors()`.

## Confirmed Technical Ground

JavaParser already provides everything the "parser-driven external expansion" needs:

- **`Processor`** (`com.github.javaparser.Processor`) with `preProcess(Provider)` and
  `postProcess(ParseResult, ParserConfiguration)`.
- **`JavaParser.parse(...)`** runs all `configuration.getProcessors()`: `preProcess` →
  parse → `postProcess` → return. The Symbol Solver and language-level post-processors
  already use this hook.
- **`GeneratedJavaParserBase.recover(...)` / `recoverStatement(...)`** already compute the
  skipped `TokenRange` and record a `Problem`; today they discard the tokens. The new fork
  wraps that `TokenRange` into an `UnparsedFragment`.
- Characterized behavior (`UngrammaticalFragmentParsingTest`): the instance
  `new JavaParser().parse(...)` returns an *unsuccessful* `ParseResult` with a **partial
  CU**; the failing method survives with an **empty body**. That empty body is the hole
  the `UnparsedFragment` will occupy.

## Module Layout

```
java-composition/
├── javaparser/                     (OLD fork — grammar-level concise bodies; deleted last)
├── javaparser-recovery/            (NEW fork — UnparsedFragment retention only)
├── parent/
├── preprocessor/                   (adds ConciseBodyProcessor; migrates off old fork)
├── java-composition-maven-plugin/
├── integration-tests/              (unchanged tests; re-pointed at new path)
└── pom.xml
```

The new fork is a second git subtree (or copy) of upstream `3.28.2`, with a **much smaller
diff** than the old one: no new grammar productions, no new `MethodDeclaration` AST fields,
no visitor changes for concise bodies — only the recovery retention and the
`UnparsedFragment` node.

## Task Breakdown

### Task 1 — Scaffold the new fork module
- Add `javaparser-recovery/` from upstream `3.28.2` sources.
- Set version `3.28.2-java-composition-recovery`; build with its own `mvnw` like the old fork.
- No functional change yet — verify it builds and installs clean.
- **Verify**: `javaparser-core-3.28.2-java-composition-recovery.jar` installs to local repo.

### Task 2 — Add the `UnparsedFragment` AST node (new fork)
- New `Node` subtype `UnparsedFragment` holding a `TokenRange` (the skipped tokens) and a
  way to read their text/images.
- Minimal metamodel/visitor wiring required for a new node (accept, clone, equals,
  hashcode, metamodel entry) — mirror how an existing simple leaf node is registered.
- **Verify**: unit test constructs an `UnparsedFragment`, round-trips through a visitor,
  and reads back its tokens.

### Task 3 — Node-returning recovery (new fork)
- Add a retention flag on `ParserConfiguration` (default off → identical behavior).
- Add `recoverAsFragment(recoveryTokenType, p)` in `GeneratedJavaParserBase`, reusing the
  existing `recover(...)` to compute the skipped `TokenRange`, wrapping it into an
  `UnparsedFragment` when retention is on.
- Touch the **method-body production** catch block in `java.jj` to call
  `recoverAsFragment` and place the fragment in the body slot when present.
- **Verify**: parsing `int m() { @ @ @ }` with retention on yields a method whose body
  slot holds an `UnparsedFragment` containing those tokens (adapt
  `UngrammaticalFragmentParsingTest` as a new-fork unit test).

### Task 4 — Shade the new fork into a distinct package
- New shaded artifact relocating `com.github.javaparser` →
  `guru.mocker.internal.recovery.javaparser`.
- **Verify**: no unshaded `com.github.javaparser` classes; no collision with the old fork's
  `guru.mocker.internal.javaparser` when both are on the classpath.

### Task 5 — `ConciseBodyProcessor` in the preprocessor
- `class ConciseBodyProcessor extends Processor`:
  - `postProcess(result, config)`: walk the CU, find `UnparsedFragment` nodes in method
    body slots, read their tokens, recognize `-> Expression ;` / `= MethodReference ;`,
    build a standard `BlockStmt`, and replace the fragment.
  - `=` form uses the Symbol Solver (as today) for static/instance disambiguation.
- Register it on the `ParserConfiguration` (with retention enabled) the preprocessor uses.
- Reuse the existing expansion logic from `ExpressionBodyTransformer` /
  `MethodReferenceBodyTransformer`, refactored to operate on fragment tokens rather than
  the old dedicated AST fields.
- **Verify**: a focused test expands both forms via the processor path.

### Task 6 — Re-point the preprocessor onto the new fork
- Switch `preprocessor` dependency from the old shaded fork to the new one.
- Parser construction: enable retention + register `ConciseBodyProcessor`.
- Remove reliance on `MethodDeclaration.expressionBody` / `methodReferenceBody`.
- **Verify (the swap trigger)**: the **existing** `integration-tests` suite (golden files +
  parsing tests) passes **unmodified** against the new path.

### Task 7 — Delete the old fork
- Remove `javaparser/` (old subtree), its reactor entry, and the old
  `3.28.2-java-composition` dependency.
- Remove now-dead concise-body AST fields/visitor code (they lived only in the old fork).
- Update pipeline: build `javaparser-recovery` instead of `javaparser`.
- **Verify**: full `mvn clean verify` green with only the new fork present.

### Task 8 — Docs
- Update README "How It Works" and Project Structure for the recovery + Processor design.
- Note the new fork is the upstream-contribution candidate (link the design doc).

## Verification Bar (Deletion Gate)

The old fork is deleted only when: the unmodified `integration-tests` suite passes against
the new fork + `ConciseBodyProcessor`, and full-reactor `mvn clean verify` is green. No test
changes are permitted as part of proving equivalence (per the agreed refinement).

## Risks

- **New-node metamodel wiring**: adding an AST node touches metamodel/visitor generation;
  must mirror an existing node precisely or generation breaks.
- **Fragment boundary granularity**: `recover(SEMICOLON, ...)` captures to the next `;`;
  confirm it captures exactly `-> expr` / `= MethodRef` for the body production.
- **Body-slot placement**: `UnparsedFragment` must be acceptable in the method-body slot
  (modeled as/adaptable to a `BlockStmt`-compatible position) so the partial CU is well
  formed enough for the processor to walk.
- **Two forks on the classpath during transition**: distinct relocation packages
  (`...internal.javaparser` vs `...internal.recovery.javaparser`) prevent collision; the
  preprocessor uses exactly one at a time.
- **Processor ordering**: if the Symbol Solver is also a processor, ensure
  `ConciseBodyProcessor` runs at the right point relative to it.
