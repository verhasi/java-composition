# Upstream issue draft — JavaParser enhancement

> **FILED:** https://github.com/javaparser/javaparser/issues/5114 (2026-08-31, author `verhasi`).
> Body posted from the `## Summary` section onward; the preamble/Title/Labels headers below
> were stripped. Kept here as the source of record.
>
> Target repo: `javaparser/javaparser` (GitHub Issues → *Feature request*).

---

## Title

Retain skipped tokens during error recovery as a first-class AST node (opt-in `UnparsedBlockStatement`)

## Labels (suggested)

`enhancement`, `parser`, `discussion`

---

## Summary

JavaParser already performs error recovery: risky productions are wrapped in
`try/catch (ParseException)`, `recover(...)` skips forward to a sync token, a `Problem`
is recorded, and the parse **continues** with a hole in the tree. Today the skipped
tokens are discarded — only the `Problem` message survives.

This proposes an **opt-in** capability to *retain* those skipped tokens as a first-class
AST node instead of discarding them, so external tooling can re-parse them and replace
the node with a valid subtree. When the flag is off (the default), behavior is
byte-for-byte identical to today.

The motivating use case is **pluggable / experimental syntax** at the
body-declaration level (e.g. a preprocessor for a not-yet-standard Java form) without
forking the grammar. But the capability is general: it turns recovery from
"skip-and-report" into "skip-report-and-optionally-keep".

## Motivation

To support syntax the standard grammar rejects (our case: concise method bodies,
`T m() -> expr;` and `T m() = Ref;`, from the draft JEP 8209434), the only option today
is to **fork `java.jj`** and add grammar productions + AST fields. That fork is a heavy,
long-lived maintenance burden and diverges from upstream on every release.

The key observation: **JavaParser already knows exactly which tokens it skipped.**
`recover(...)` computes a `TokenRange` spanning precisely the discarded tokens. It then
throws that range away. If recovery could *optionally keep* that range in the tree, all
the interpretation could move **out** of the parser into ordinary library code running
on **stock** JavaParser — no grammar fork.

## What already exists (so this is a small change)

- Recovery is an accepted concept: `GeneratedJavaParserBase.recover(int, ParseException)`
  and `recoverStatement(...)` skip-and-report, and the parse continues accumulating
  `Problem`s.
- The instance API (`new JavaParser().parse(...)`) already returns an **unsuccessful
  `ParseResult` containing a partial `CompilationUnit`** — the recovered method survives
  with an **empty body** where the skipped tokens were. So the tree and the placement
  slot already exist; only the tokens are dropped.
- Token-stream access (`token()`, `range(...)`) already exists in the base class.

The single missing capability: **keep the already-computed `TokenRange` in that hole**
instead of discarding it.

## Proposal

### 1. A retention flag (default off)

`ParserConfiguration.setRetainUnparsedTokens(boolean)` / `isRetainUnparsedTokens()`,
default `false`, threaded into the generated parser.

### 2. A recovery node

An `UnparsedBlockStatement` that holds the skipped `TokenRange`. It occupies the body slot
where a `BlockStmt` was expected, so it needs to be *block-shaped*:

```java
// com.github.javaparser.ast.stmt
public class UnparsedBlockStatement extends BlockStmt {
    // carries the retained TokenRange (the skipped tokens);
    // generator-wired like any other node (metamodel, visitors, clone, equals/hash)
}
```

Extending `BlockStmt` lets it drop directly into `MethodDeclaration.body` (typed
`BlockStmt`) with zero metamodel churn on the declaration side.

### 3. A recovery helper that reuses `recover`

```java
// GeneratedJavaParserBase
BlockStmt recoveredBlock(JavaToken begin, TokenRange errorRange) {
    if (retainUnparsedTokens) {
        return new UnparsedBlockStatement(range(begin, token())); // keep the tokens
    }
    BlockStmt block = new BlockStmt(range(begin, token()), new NodeList<>());
    block.setParsed(Node.Parsedness.UNPARSABLE);                  // historical behavior
    return block;
}
```

When retention is off, this reproduces exactly today's outcome (empty, `UNPARSABLE`
block). When on, it parks the skipped tokens in the node and the parse continues.

### 4. The grammar call site (minimal, additive)

At the method-body production, the catch block recovers to `;` and installs the node:

```
try {
    ( body = Block() | ";" )
} catch (ParseException e) {
    JavaToken bodyBegin = token();
    TokenRange errorRange = recover(SEMICOLON, e);   // existing skip-and-report
    body = recoveredBlock(bodyBegin, errorRange);    // keep tokens iff retention on
}
```

No lookahead / FIRST / FOLLOW / decision-table changes. The catch block is the only
grammar touch, and only at productions where retention is desired.

### 5. Interpretation lives entirely outside the parser

```java
ParserConfiguration cfg = new ParserConfiguration().setRetainUnparsedTokens(true);
CompilationUnit cu = new JavaParser(cfg).parse(src).getResult().get();

for (UnparsedBlockStatement u : cu.findAll(UnparsedBlockStatement.class)) {
    Node replacement = myTool.reparse(u.getTokenRange().get()); // any grammar/logic
    u.replace(replacement);                                     // standard replace()
}
```

No user code runs *inside* the parser; the parser only parks tokens.

## Guarantees (the pitch)

- **Off by default → identical behavior.** Same empty `UNPARSABLE` block, same
  `Problem`, byte-for-byte.
- **No in-parser user code.** The parser parks tokens in a node; all interpretation is
  external. Far smaller than an in-parse handler SPI.
- **Reuses existing plumbing.** The skipped `TokenRange` and `Problem` reporting already
  exist; this adds a node type + one opt-in wrap step.
- **Touches only the recovery path.** No grammar-decision changes.

## Proven in practice

We have a working fork of `3.28.2` implementing exactly the above and a preprocessor for
concise method bodies built entirely on top of it as an **external** post-parse pass:

- **Stage 1** — parse with retention on → concise bodies become `UnparsedBlockStatement`;
  valid bodies stay normal `BlockStmt`.
- **Stage 2** — a post-parse pass finds each `UnparsedBlockStatement`, reads the marker
  (`->` / `=`), and re-parses the payload (which is *standard Java*, e.g. `items.size()`,
  `Math::max`) with **stock** parsing.
- **Stage 3** — expand to a 100% standard `MethodDeclaration` (the `=` form uses the
  Symbol Solver, demonstrating non-trivial external re-parsing works).

The grammar is otherwise **pristine** — all concise-specific logic is external library
code. This is the evidence that the retention hook is sufficient and that interpretation
does not need to live in core.

(Reference implementation and worked example available; happy to link/PR.)

## Open questions for maintainers

1. **Node modeling.** We chose `UnparsedBlockStatement extends BlockStmt` (block-shaped by
   slot, unparsed by nature) so it fits `MethodDeclaration.body` with no metamodel churn.
   Alternatives: a generic `extends Node` with placement rules, or a dedicated optional
   "unparsed body" association. Preference?
2. **Which productions get retention.** Method body is the obvious first (and only one we
   need). Statement-level (`UnparsableStmt` already exists) and compilation-unit-level
   (`recover(EOF)`) retention are natural extensions — worth scoping separately.
3. **Naming.** `retainUnparsedTokens` / `UnparsedBlockStatement` — open to bikeshedding.
4. **Appetite.** Is a deliberately-not-valid-Java node acceptable in core given the
   default-off, constrained-placement mitigations? If not, would a narrower
   "retained tokens on the existing `UNPARSABLE` block" shape be preferable?

## Scope boundary

- Targets the `ParseException` (grammar-level) recovery path only. Syntax needing a
  genuinely new **token** (tokenizer-level `TokenMgrException`) is out of scope — our
  forms use only tokens standard Java already lexes.
- Recovery granularity is sync-token based (`recover(SEMICOLON, ...)`); the external
  re-parser must tolerate that the captured span may not be a clean sub-tree. For
  `-> expr ;` it captures exactly `-> expr`.
