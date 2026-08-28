# Design: Parser Recovery SPI (Fork-Free Syntax Extension)

## Status

Exploratory. This document captures a candidate architecture to eliminate the
JavaParser fork by extending parse-failure handling, and to potentially contribute
that mechanism upstream. It does not replace the current fork-based implementation;
it describes what a fork-free future could look like.

## Problem

The current implementation forks JavaParser to teach its JavaCC grammar the two
concise method body forms (`-> expr ;` and `= MethodRef ;`). Forking has ongoing
cost: the grammar and AST/visitor changes must be rebased on every upstream upgrade.

We want a mechanism where:
- The primary dependency is **unmodified** JavaParser.
- New syntax is handled by a **recovery step** invoked only when standard parsing fails.
- Consumers who never use the new syntax pay nothing and touch only stock JavaParser.

## Key Facts That Shape the Design

### Two error stages, two exception types

JavaParser fails in two distinct stages:

| Stage | Class | Kind | Meaning |
|-------|-------|------|---------|
| Tokenizer (`GeneratedJavaParserTokenManager`) | `TokenMgrException` | unchecked | A character sequence forms no valid token at all |
| Parser (`GeneratedJavaParser`) | `ParseException` | checked | Tokens are valid, but their sequence matches no grammar production |

Verified against the generated sources: the parser throws `ParseException` at 76
sites; the token manager throws `TokenMgrException` at 2 sites; the parser holds
**zero** references to `TokenMgrException`.

### Our syntax is a grammar problem, not a token problem

The concise forms use only tokens that standard Java already tokenizes (`->`, `=`,
identifiers, `;`). They fail purely at the **grammar** level. Therefore:

- The recovery hook belongs on the **parser's `ParseException` path**.
- The tokenizer needs no changes.
- Future syntax that introduced a genuinely new token would surface as
  `TokenMgrException` and require a separate (harder) tokenizer hook. The current
  forms do not, so this design scopes to the parser.

### On a `ParseException`, the result is dropped entirely

When `GeneratedJavaParser` throws, its recursive-descent stack unwinds completely.
There is no partial AST and no resumable position. **Stock JavaParser cannot be
resumed mid-parse from outside.** Only code *inside* the parser (owning the try/catch
at the production) can contribute a node and continue.

This is the crux that separates the two implementation paths below.

## Two Implementation Paths

### Path A — External member-boundary fallback (no upstream change)

Because the result is fully dropped, an external tool can only re-parse from scratch
with a modified strategy:

```
parse(source):
    try:
        return stockJavaParser.parse(source)              // untouched JavaParser
    catch ParseException e:
        member  = locateFailingMember(source, e)           // via token range in e
        blanked = replaceMemberWithStub(source, member)    // valid placeholder
        cu      = stockJavaParser.parse(blanked)           // stock parser, valid input
        node    = fragmentParser.parseBodyDeclaration(member) // OUR grammar, fragment only
        splice(cu, node)                                   // swap stub for real node
        return cu
```

Properties:
- **Pro**: depends primarily on stock JavaParser; our grammar is a fallback only.
- **Con**: still requires our own fragment parser (a small forked grammar scoped to
  parsing a single member), plus external bookkeeping to blank/splice.
- **Con**: double parse on failure; complexity in reliably locating the failing member
  and handling nested/multiple failures.
- **Does not resume** the stock parser — it re-parses a sanitized whole file.

### Path B — Upstream recovery SPI (the proposed contribution)

Enrich the parser's failure path so the result is not unconditionally dropped. At
member/statement boundaries, before throwing, the parser consults a registered
recovery handler; if the handler produces a node, the parser uses it and continues.

```
// Inside the parser, at a member/statement choice point (conceptual):
try {
    return standardProduction();
} catch (ParseException e) {
    for (RecoveryHandler h : handlers) {
        Optional<Node> recovered = h.recover(parserState, e);
        if (recovered.isPresent()) {
            return recovered.get();     // parser retains control, continues
        }
    }
    throw e;                            // no handler → identical to today
}
```

Properties:
- **Pro**: the parser retains control and can genuinely continue — the thing external
  code fundamentally cannot do.
- **Pro**: default behavior (no handler registered) is **byte-for-byte identical** to
  today. Zero risk to existing users.
- **Pro**: touches only the **failure path** — not lookahead tables, not FIRST/FOLLOW,
  not decision logic. The parser makes all normal decisions unchanged; the hook fires
  only where it would otherwise give up.
- **Con**: requires an upstream change to JavaParser (or a minimal fork that adds only
  this seam, which is far smaller and more stable than the current grammar fork).

## Proposed Upstream Change (Path B)

**Scope it narrowly to maximize acceptance odds:**

1. **Hook location**: fire recovery only at top-level **member** and **statement**
   boundaries, not at every choice point. This is contained and sufficient for
   body-declaration-level extensions like ours.

2. **SPI surface** (`RecoveryHandler`):
   ```java
   public interface RecoveryHandler {
       /**
        * Attempt to recover from a parse failure at the current position.
        * @param context exposes the token stream + current position so the
        *                handler can re-read the fragment it recovers.
        * @param failure the ParseException the parser was about to throw.
        * @return a node to substitute and continue with, or empty to decline.
        */
       Optional<Node> recover(RecoveryContext context, ParseException failure);
   }
   ```

3. **Parser-state access**: the `RecoveryContext` must expose enough state — current
   token, the token stream, position — for the handler to read the fragment it
   recovers. (Lookahead has already consumed tokens; the handler needs to see them.)

4. **Decline-vs-recover contract**: a handler returning empty means "not mine, keep
   trying / throw as normal." A handler must not silently mask genuine syntax errors;
   if no handler recovers, the original `ParseException` is thrown unchanged.

5. **Registration**: via `ParserConfiguration` (explicit, per-parser) rather than
   global `ServiceLoader`, to avoid surprising global behavior. `ServiceLoader` can
   be layered on later if desired.

6. **Guarantee**: no handler registered → identical to current behavior. This is the
   headline property for the upstream pitch.

## How the Concise-Body Feature Maps On (Proof of Generality)

Our feature becomes the **first `RecoveryHandler`**, proving the seam is general and
not concise-body-specific:

- Standard parse hits `-> expr ;` after a method header → `ParseException`.
- Our handler recognizes the position (method body), reads `-> Expression ;` (or
  `= MethodReference ;`) from the token stream, builds a `BlockStmt`
  (`{ return expr; }` or `{ expr; }`), and returns it.
- Parser continues with the rest of the file.
- No grammar fork, no AST field additions — the expansion happens in the handler,
  producing only standard AST nodes.

This also removes the need for the current `MethodDeclaration.expressionBody` /
`methodReferenceBody` AST fields, since recovery yields a standard `BlockStmt`.

## Staged Delivery (Risk Minimization)

Per the principle of starting simple:

1. **Stage 1 — no SPI, hardcoded.** Prototype the recovery mechanism with the two
   concise forms hardcoded into the recovery path. Prove "fragment parse + node
   substitute + continue" works end to end.
2. **Stage 2 — extract the SPI.** Once the mechanism is proven, factor the hardcoded
   logic behind the `RecoveryHandler` interface. Concise bodies become the reference
   handler.
3. **Stage 3 — upstream discussion.** Bring the narrowly-scoped design (member/
   statement boundaries only, config-based registration, no-handler-identical
   guarantee) to the JavaParser maintainers, with the concise-body handler as the
   worked example.

## Risks and Open Questions

- **Locating the boundary reliably (Path A)**: mapping a `ParseException` token range
  back to the exact failing member is error-prone with malformed input; nested and
  multiple failures compound it.
- **Parser-state exposure (Path B)**: how much internal state must the SPI expose
  without leaking JavaCC implementation details or freezing them as public API?
- **Lookahead already consumed**: the handler must re-read tokens the parser's
  lookahead already advanced past; the context API must make the pre-failure position
  available.
- **Performance**: Path A double-parses on failure. Path B recovers in-line and avoids
  re-parsing, which is a further argument for the upstream approach.
- **New tokens are out of scope**: any future syntax needing a token standard Java
  can't lex surfaces as `TokenMgrException` and needs a separate tokenizer hook. Not
  addressed here.
- **Maintainer appetite**: the change is bounded and default-safe, but any addition to
  a parser's failure path invites scrutiny. Narrow scope and the identical-default
  guarantee are the strongest arguments.

## Recommendation

Path B (upstream recovery SPI) is the target architecture: it lets the parser retain
control and continue, keeps default behavior identical, and touches only the failure
path. Path A (external member-boundary fallback) is a viable interim that needs no
upstream change but still requires a scoped fragment grammar and external splicing.

Prototype Stage 1 against the current fork to validate the mechanism before investing
in the SPI or opening an upstream discussion.
