# Design: Retained Unparsed Fragments for Pluggable Syntax (Upstream Contribution)

## Status

Exploratory design for an **upstream JavaParser contribution**. The goal is to
eliminate the need to fork JavaParser's grammar for body-declaration-level syntax
extensions (such as our concise method bodies) by having JavaParser's **existing**
error-recovery retain the skipped tokens as a first-class AST node, which an external
pass can then re-parse and replace.

This document covers **only** the upstream-contribution approach. Purely external
"parse, catch, re-parse and splice the whole file" workarounds are out of scope:
JavaParser's recovery call sites are internal, so recovery cannot be initiated from
outside.

## Background: JavaParser Already Has Recovery

JavaParser is not a "throw and discard everything" parser. `GeneratedJavaParserBase`
provides recovery primitives invoked from `catch (ParseException)` blocks embedded in
the generated parser (emitted from the `java.jj` grammar):

```java
/* Called from within a catch block to skip forward to a known token,
   and report the occurred exception as a problem. */
TokenRange recover(int recoveryTokenType, ParseException p) { ... }

/* Brace-aware variant: skips a statement, tracking { } nesting. */
TokenRange recoverStatement(int recoveryTokenType, int lBraceType, int rBraceType, ParseException p) { ... }
```

Facts that shape this design:

1. **Recovery is an accepted concept.** Risky productions are wrapped in try/catch;
   recovery records a `Problem` in `List<Problem> problems` and the parse **continues**.
2. **The token-stream access needed already exists**: `token()`, `getNextToken()`,
   `getToken(int)`, and `range(...)` helpers in the base class.
3. **Recovery today skips-and-reports only.** `recover` advances past the offending
   tokens to a sync token (e.g., `;`), records a `Problem`, and **leaves a hole** —
   crucially, it already computes a `TokenRange` spanning exactly the skipped tokens,
   then discards everything but the problem message.

The skipped `TokenRange` is the key asset: recovery already knows precisely which
tokens it threw away. Today it drops them. This design **keeps** them.

## The Two Error Stages (Scope Boundary)

| Stage | Class | Kind | Meaning |
|-------|-------|------|---------|
| Tokenizer (`GeneratedJavaParserTokenManager`) | `TokenMgrException` | unchecked | A character sequence forms no valid token |
| Parser (`GeneratedJavaParser`) | `ParseException` | checked | Valid tokens, but the sequence matches no production |

Verified: the parser throws `ParseException` (76 sites) and holds **zero** references
to `TokenMgrException`. Our concise forms use only tokens standard Java already lexes,
so they fail at the grammar level. This design targets the `ParseException` recovery
path exclusively; syntax needing a genuinely new token is out of scope.

## Primary Proposal: Retained Unparsed Fragments

Instead of discarding skipped tokens into a `Problem`, recovery optionally produces a
first-class AST node — `UnparsedFragment` — that holds the skipped `TokenRange`. An
**external** pass later walks the tree, finds these nodes, re-parses their captured
tokens with any grammar/logic it likes, and replaces them.

### Why this is the cleanest upstream change

- **The parser's only new job is: don't throw the tokens away.** No callback into user
  code mid-parse, no handler registry, no `RecoveryContext` surface. The parser parks
  the already-computed `TokenRange` in a node and continues.
- **All interpretation moves outside JavaParser.** Concise-body expansion, Symbol Solver
  type resolution, everything happens in an external post-parse pass over
  `UnparsedFragment` nodes — on stock JavaParser, no forked productions.
- **It matches JavaParser's existing philosophy** of lenient parsing that accumulates
  problems and keeps going. "Retain what you skipped" is a natural extension of that,
  not a new subsystem.
- **It is inherently multi-round-friendly** (see "Relation to Multi-Round Processing").

### The node

```java
package com.github.javaparser.ast;

/**
 * A placeholder node holding a range of tokens that the parser skipped during
 * error recovery. Present only when fragment retention is enabled. Represents a
 * region the standard grammar could not parse; external tooling may re-parse the
 * captured tokens and replace this node with a valid subtree.
 *
 * <p>An UnparsedFragment is deliberately NOT valid Java on its own. Trees that may
 * contain it must be produced with the retention flag enabled and treated as
 * pending further processing.
 */
public class UnparsedFragment extends Node {
    private TokenRange fragmentTokens;   // the skipped tokens
    // getFragmentTokens(), accept(visitor), clone(), metamodel, etc.
}
```

### The recovery change (in `GeneratedJavaParserBase`)

`recover`/`recoverStatement` already compute the skipped `TokenRange`. The change is to
optionally construct and return an `UnparsedFragment` from it:

```java
/* Configuration flag; default false => behavior identical to today. */
boolean retainUnparsedFragments;

/* Node-producing recovery: when retention is on, return a fragment node holding
   the skipped tokens; otherwise behave exactly as before (skip-and-report only). */
Optional<UnparsedFragment> recoverAsFragment(int recoveryTokenType, ParseException p) {
    TokenRange skipped = recover(recoveryTokenType, p);   // existing skip-and-report
    if (retainUnparsedFragments && skipped != null) {
        UnparsedFragment fragment = new UnparsedFragment(skipped);
        return Optional.of(fragment);
    }
    return Optional.empty();
}
```

Note this **reuses** the existing `recover` verbatim (including its `Problem` reporting)
and merely wraps the already-computed `TokenRange`. When `retainUnparsedFragments` is
false, the method returns empty and nothing changes.

### The grammar call site (minimal `java.jj` touch)

At productions where retention is desired (e.g., the method-body production), the catch
block installs the fragment where the hole would be:

```
try {
    body = Block();
} catch (ParseException p) {
    Optional<UnparsedFragment> fragment = recoverAsFragment(SEMICOLON, p);
    if (fragment.isPresent()) {
        // place the fragment in the body slot (see "AST Modeling" below)
        bodyFragment = fragment.get();
    }
    // else: existing behavior — Problem recorded, body remains absent
}
```

This is the one honest cost: recovery is invoked from grammar `catch` blocks, so
enabling fragment retention at a production requires touching that production's catch
block. This is **far smaller** than adding grammar productions or new syntax rules — it
is additive, localized, and alters no lookahead, FIRST/FOLLOW, or parse decision.

If a target production already calls `recover` on failure, retention there may require
**only** the base-class change plus swapping `recover` for `recoverAsFragment` — worth
verifying against the actual grammar per production.

### The external re-parse pass (lives entirely in our library)

```
cu = stockJavaParser.parse(source, config.setRetainUnparsedFragments(true));
for (UnparsedFragment f : cu.findAll(UnparsedFragment.class)) {
    Node replacement = ourFragmentParser.parseAndExpand(f.getFragmentTokens());
    f.replace(replacement);   // standard JavaParser node replacement
}
```

No forked grammar productions, no AST field additions to `MethodDeclaration`. The
concise-body logic (including Symbol Solver resolution for the `=` form) is ordinary
library code operating on captured tokens.

## AST Modeling Question

`UnparsedFragment` must occupy the slot where the recovered construct would have gone.
For a method body, the body slot expects a `BlockStmt`. Options to align with
maintainers:

1. **Fragment as a `Statement`/`BlockStmt`-compatible node** so it can sit in the body
   slot directly. Simplest for the method-body case; constrains where fragments may
   appear.
2. **A dedicated optional "unparsed body" association** on the relevant declarations,
   populated only under retention. More explicit, but touches more of the metamodel.
3. **A generic `UnparsedFragment extends Node`** with placement rules enforced by the
   productions that create it. Most general; requires care that consumers guard for it.

Whichever is chosen, the node is **only present when retention is enabled**, so default
trees remain exactly as today. This is the key mitigation for introducing a node that
is deliberately not valid Java.

## Guarantees (the Upstream Pitch)

- **Retention off (default) → identical behavior.** `recoverAsFragment` returns empty
  and delegates to the unchanged `recover`. Byte-for-byte unchanged for current users.
- **No user code runs inside the parser.** The parser only parks tokens in a node; all
  interpretation is external. This is a far smaller ask than an in-parse handler SPI.
- **Reuses existing plumbing.** The skipped `TokenRange` and `Problem` reporting already
  exist; we add a node type and an optional wrap step.
- **Touches only the recovery path.** No lookahead, decision-table, or FIRST/FOLLOW
  changes.

## Relation to Multi-Round Processing (Task 7)

`UnparsedFragment` retention is a natural substrate for round-based processing:

- Each round parses with retention on, collects `UnparsedFragment` nodes, expands those
  it understands, and replaces them.
- Fragments a round cannot expand are left in place for a later round (or reported).
- The loop terminates when a round produces no changes (no remaining fragments it can
  expand). This is exactly the convergence model described for Task 7, now grounded in
  a concrete AST representation rather than string re-processing.

## Alternative (Not Preferred): In-Parse Node-Returning Handler

A previously considered variant has recovery consult a registered `RecoveryHandler`
that returns the final node during parsing (single pass, no fragment node). Trade-offs
vs. the primary proposal:

- **Pro**: single pass; no transient "invalid" node in the tree.
- **Con**: requires a handler registry and a `RecoveryContext` surface **inside core**,
  and invites user code to run mid-parse — a larger, harder-to-accept upstream change.
- **Con**: couples interpretation to parse time; less friendly to multi-round.

The two are not mutually exclusive: core could ship fragment retention (minimal, safe),
and an optional layer could offer the in-parse handler for tools wanting single-pass.
**Lead with fragment retention** — it is the smaller, cleaner, more general contribution.

## Staged Delivery

1. **Stage 1 — hardcoded proof.** In a minimal fork, add `UnparsedFragment`,
   `recoverAsFragment`, and wire the method-body catch block under a retention flag.
   Re-parse fragments with the concise-body logic in an external pass. Validate against
   the existing test corpus.
2. **Stage 2 — solidify the model.** Settle the AST-placement choice, metamodel/visitor
   integration, `replace()` semantics, and the retention flag on `ParserConfiguration`.
3. **Stage 3 — upstream proposal.** Pitch fragment retention to JavaParser maintainers:
   default-off, reuses existing recovery, no in-parser user code. Concise method bodies
   are the worked example; the `=` form demonstrates that non-trivial external
   re-parsing (with type resolution) is supported.

## Risks and Open Questions

- **Grammar call-site touch is unavoidable.** Retention at a production requires that
  production's catch block to call `recoverAsFragment`. Which productions get it is a
  design decision to align with maintainers (likely method body first).
- **Recovery granularity is token-type-based.** `recover(SEMICOLON, p)` skips to the
  next `;`; for `-> expr ;` this captures exactly `-> expr`, but other constructs may
  capture more/less than a clean sub-tree. The external re-parser must tolerate this.
- **AST placement of a deliberately-invalid node.** Introducing `UnparsedFragment` is a
  philosophical step for JavaParser; the default-off guarantee and constrained
  placement are the mitigations.
- **Consumer awareness.** Tools consuming a retention-enabled tree must guard for
  `UnparsedFragment` (e.g., before compilation-oriented processing). Off by default
  keeps this opt-in.
- **`=` form type resolution.** Handled entirely in the external pass via Symbol Solver;
  not a concern for the core change.
- **Maintainer appetite.** Strongest arguments: it reuses the existing recovery concept,
  is purely additive, runs no user code in the parser, and is provably identical when
  off.

## Recommendation

Pursue **retained unparsed fragments** as the upstream contribution. JavaParser's
recovery already computes the skipped `TokenRange`; the change is simply to keep it as
an `UnparsedFragment` node under an opt-in flag, then re-parse and replace externally.
This is smaller and more defensible than an in-parse handler SPI, keeps all
interpretation in our library on stock JavaParser, and provides a natural foundation for
multi-round processing. Prototype Stage 1 against a minimal fork, then open the upstream
discussion with concise method bodies as the reference case.
