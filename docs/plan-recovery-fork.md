# Plan: Recovery-Fork + Processor Architecture

## Goal

Re-implement concise method bodies on top of a **minimal, general** JavaParser fork that
retains unparsed tokens as an `UnparsedBlockStatement` node, plus a **`Processor`** (registered
on `ParserConfiguration`) that expands those fragments. This replaces the current
grammar-level fork. Both forks live side by side until the new path passes the existing
tests unchanged; then the old fork is deleted.

## Target Architecture

```
Source (.java with concise bodies)
   │
   ▼  STAGE 1 — Parse (new fork, general, no concise knowledge)
   │            standard parse; where a method body cannot be parsed, recovery
   │            retains the skipped tokens as an UnparsedBlockStatement placed in
   │            the body slot (the exact point an empty BlockStmt goes today)
   ▼  CompilationUnit with UnparsedBlockStatement nodes
   │
   ▼  STAGE 2 — Concise parsing extension (our parser layer)
   │            find UnparsedBlockStatement nodes; for each that IS a concise method
   │            declaration, re-parse its tokens and replace it with a typed
   │            ConciseMethodDeclaration (our AST type, carries -> expr / = MethodRef)
   ▼  CompilationUnit with ConciseMethodDeclaration nodes
   │
   ▼  STAGE 3 — Preprocessor transformation (expansion)
   │            replace each ConciseMethodDeclaration with a 100% stock
   │            MethodDeclaration (standard BlockStmt body; Symbol Solver for `=`)
   ▼  Fully-expanded standard-Java CompilationUnit (no custom types remain)
```

The pipeline deliberately separates **recognition** (Stage 2: tokens → structured concise
node) from **transformation** (Stage 3: concise node → standard Java). Each stage is
independently testable and replaceable. Stages 2 and 3 are *our* code; only Stage 1 lives
in the fork.

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
   the new fork + Stage 2/3 recognition and transformation **without modifying any test**. Then delete the
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
  skipped `TokenRange` and record a `Problem`. Crucially, the four grammar call sites fall
  into a **three-level recovery taxonomy** (verified against `java.jj`):

  | Level | Call site | Method | Result today | Tokens retained? |
  |-------|-----------|--------|--------------|------------------|
  | Statement | Statement productions | `recoverStatement(SEMICOLON, LBRACE, RBRACE, e)` | **`UnparsableStmt(errorRange)`** | **Yes — already** |
  | Block body | `Block()` production | `recover(RBRACE, e)` | empty `BlockStmt` marked `UNPARSABLE` | **No — discarded** |
  | Compilation unit | top-level CU | `recover(EOF, e)` | empty `CompilationUnit` marked `UNPARSABLE` | **No — discarded** |

  Key insight: JavaParser **already has a token-retaining recovery node** — `UnparsableStmt`
  holds the `errorRange` for the statement level. This is precedent: the enhancement is to
  extend the same courtesy to the block-body and CU levels, which currently discard tokens.

  These levels have **different structural slots**, so a single `extends Node` type cannot
  serve all three — each needs a node compatible with its position:
  - Statement slot → `UnparsableStmt` (exists)
  - `BlockStmt` body slot → **`UnparsedBlockStatement extends BlockStmt`** (this project's
    Task 2 — the level concise method bodies need)
  - `CompilationUnit` slot → a future `UnparsedCompilationUnit` (general story only)

- Characterized behavior (`UngrammaticalFragmentParsingTest`): the instance
  `new JavaParser().parse(...)` returns an *unsuccessful* `ParseResult` with a **partial
  CU**; the failing method survives with an **empty body** (the block-body `recover(RBRACE)`
  path). That empty body is the hole the `UnparsedBlockStatement` will occupy.

## Module Layout

```
java-composition/
├── javaparser/                     (OLD fork — grammar-level concise bodies; deleted last)
├── javaparser-recovery/            (NEW fork — UnparsedBlockStatement retention only)
├── parent/
├── preprocessor/                   (adds Stage 2/3 recognition + transformation; off old fork)
├── java-composition-maven-plugin/
├── integration-tests/              (unchanged tests; re-pointed at new path)
└── pom.xml
```

The new fork is a second git subtree (or copy) of upstream `3.28.2`, with a **much smaller
diff** than the old one: no new grammar productions, no new `MethodDeclaration` AST fields,
no visitor changes for concise bodies — only the recovery retention and the
`UnparsedBlockStatement` node.

## Task Breakdown

### Task 1 — Scaffold the new fork module
- Add `javaparser-recovery/` from upstream `3.28.2` sources.
- Set version `3.28.2-java-composition-recovery`; build with its own `mvnw` like the old fork.
- No functional change yet — verify it builds and installs clean.
- **Verify**: `javaparser-core-3.28.2-java-composition-recovery.jar` installs to local repo.

### Task 2 — Add the `UnparsedBlockStatement` AST node (new fork)
- New node `UnparsedBlockStatement **extends BlockStmt**, holding the skipped `TokenRange`
  (the tokens) and a way to read their text/images.
- **Modeling decision (resolved):** it extends `BlockStmt` so it fits the existing
  method-body slot (`MethodDeclaration.body : BlockStmt`) at the *exact* position stock
  recovery puts an empty `BlockStmt` today — **no `MethodDeclaration` change**. The name is
  honest: it is a *block-shaped* body-slot occupant that is *unparsed*. It guarantees
  nothing about the meaning of its tokens (Stage 2 decides that); "block" describes the
  slot, not the content.
- Minimal metamodel/visitor wiring for a new node (accept, clone, equals, hashcode,
  metamodel entry) — mirror `BlockStmt`'s registration since it is the direct supertype.
- **Verify**: unit test constructs an `UnparsedBlockStatement`, round-trips through a visitor,
  reads back its tokens, and confirms it is assignable to a `MethodDeclaration` body slot.

### Task 3 — Token-retaining recovery at the method-body position (new fork)
- Add a retention flag on `ParserConfiguration` (default off → identical behavior).
- **Correction from investigation:** concise bodies (`-> expr;` / `= ref;`) fail at the
  **method-body position** in the `MethodDeclaration` production — where `{`/`;` is
  expected — *not* inside `Block()`. The `ParseException` there otherwise propagates to
  the CU-level recovery and discards the whole file. So the recovery `catch` is added
  around the method-body choice `( Block() | ";" )` in `MethodDeclaration` (Option A):
  on failure, `recover(SEMICOLON, e)` skips to the method's terminating `;` and
  `recoveredBlock(begin, range)` yields an `UnparsedBlockStatement` (retention on) or the
  historical empty `BlockStmt` (off). The method signature (name, return type, params) is
  preserved; only the body becomes the retained node.
- Shared helper `recoveredBlock(begin, errorRange)` in `GeneratedJavaParserBase`; the
  block-content site (`recover(RBRACE)`) also uses it, so block-content recovery retains
  too — but the concise path is the method-body site.
- **Verify** (`BlockBodyRecoveryRetentionTest`, 6 tests): concise `->` and `=` bodies each
  yield a method whose signature is preserved and whose body is an `UnparsedBlockStatement`
  with a token range; block-content `{ @ @ @ }` also retains; retention off/default is
  identical to historical (empty `BlockStmt`); valid code is unaffected.

### Task 4 — (folded into Task 6) Distinct relocation package
- Shading happens in the **preprocessor** module, not the fork; the fork just publishes
  `javaparser-core` / `javaparser-symbol-solver-core` at version
  `3.28.2-java-composition-recovery` (plain artifacts, no classifier — verified installed).
- The distinct relocation package (`guru.mocker.internal.recovery.javaparser`) is therefore
  a preprocessor shade-config change realized when re-pointing (Task 6). Building a
  throwaway shade module now would duplicate that work, so this step is folded into Task 6,
  which verifies: no unshaded `com.github.javaparser` classes and no collision with the old
  fork's `guru.mocker.internal.javaparser`.

### Task 5 — Concise recognition + transformation in the preprocessor

Implemented as two separable steps (Stages 2 and 3), both in our layer:

- **`ConciseMethodDeclaration extends MethodDeclaration`** (our AST type): carries the
  concise form (`-> expr` or `= MethodRef`). This is where the concise info lives now —
  a subclass in our code, not fields on the fork's `MethodDeclaration`.
- **Stage 2 — recognition** (a `Processor` registered on `ParserConfiguration`):
  `postProcess` walks the CU, finds `UnparsedBlockStatement` nodes, and for each that IS a
  concise method declaration, re-parses its tokens and replaces it with a
  `ConciseMethodDeclaration`. Pure recognition — no expansion.
- **Stage 3 — transformation**: replaces each `ConciseMethodDeclaration` with a 100% stock
  `MethodDeclaration` (standard `BlockStmt` body). The `=` form uses the Symbol Solver for
  static/instance disambiguation. This reuses the existing expansion logic from
  `ExpressionBodyTransformer` / `MethodReferenceBodyTransformer`, refactored to read from
  `ConciseMethodDeclaration` rather than the old fork's dedicated AST fields.
- **Verify**: focused tests for each stage independently — Stage 2 (fragment →
  `ConciseMethodDeclaration`) and Stage 3 (`ConciseMethodDeclaration` → standard
  `MethodDeclaration`) — plus an end-to-end test through both.

### Task 6 — Re-point the preprocessor onto the new fork
- Switch `preprocessor` dependency from the old shaded fork to the new one.
- Parser construction: enable retention + register the Stage 2 recognition processor.
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

## General Recovery Story — Remaining Tasks (beyond concise bodies)

These complete the *general* recovery enhancement for the upstream contribution. They are
**not needed for concise method bodies** (which only require the block-body level, Task 2),
so they sit at the end of the line.

### Task 9 — Statement-level: confirm `UnparsableStmt` sufficiency
- The statement recovery path (`recoverStatement`) **already** produces `UnparsableStmt`
  holding the `errorRange` — token retention already works at this level.
- Verify no change is needed; document `UnparsableStmt` as the statement-level precedent
  that justifies the block-body and CU-level additions.
- **Verify**: a characterization test showing `UnparsableStmt` retains the token range for a
  statement-level recovery.

### Task 10 — Compilation-unit level: `UnparsedCompilationUnit`
- The CU recovery path (`recover(EOF, e)`) currently produces an empty `CompilationUnit`
  marked `UNPARSABLE`, **discarding** the tokens.
- Add a token-retaining variant compatible with the CU slot (e.g., a `CompilationUnit` that
  carries the skipped `TokenRange`, or an `UnparsedCompilationUnit`), mirroring how
  `UnparsedBlockStatement` retains at the block level.
- **Verify**: parsing a wholly ungrammatical CU with retention on yields a CU that retains
  the skipped tokens rather than discarding them.

## Verification Bar (Deletion Gate)

The old fork is deleted only when: the unmodified `integration-tests` suite passes against
the new fork + Stage 2/3 recognition and transformation, and full-reactor `mvn clean verify` is green. No test
changes are permitted as part of proving equivalence (per the agreed refinement).

## Risks

- **New-node metamodel wiring**: adding an AST node touches metamodel/visitor generation;
  must mirror an existing node precisely or generation breaks.
- **Fragment boundary granularity**: `recover(RBRACE, ...)` captures to the closing brace;
  confirm it captures exactly the concise body tokens for the block-body production.
- **Body-slot placement (resolved)**: `UnparsedBlockStatement extends BlockStmt`, so it fits
  the `MethodDeclaration.body : BlockStmt` slot directly — no `MethodDeclaration` change. It
  sits at the exact position stock recovery puts an empty `BlockStmt`, so the partial CU is
  well formed for Stage 2 to walk.
- **Two forks on the classpath during transition**: distinct relocation packages
  (`...internal.javaparser` vs `...internal.recovery.javaparser`) prevent collision; the
  preprocessor uses exactly one at a time.
- **Processor ordering**: if the Symbol Solver is also a processor, ensure the Stage 2
  recognition processor runs at the right point relative to it.
