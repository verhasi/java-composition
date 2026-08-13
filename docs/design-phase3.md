# Design: Phase 3 — Wildcard Delegation and Processing Refactoring

## Processing Architecture Refactoring

Before implementing wildcard delegation, the processing pipeline is refactored
to the round-based model and single-walk transformation.

### Single-Walk Concise Method Transformer

The `ExpressionBodyTransformer` and `MethodReferenceBodyTransformer` are unified
under a single AST visitor that delegates to the appropriate specialist:

```java
public class ConciseMethodTransformer extends ModifierVisitor<Void> {
    private final ExpressionBodyTransformer expressionDelegate;
    private final MethodReferenceBodyTransformer referenceDelegate;

    @Override
    public Visitable visit(MethodDeclaration md, Void arg) {
        if (md.hasExpressionBody()) {
            expressionDelegate.visit(md, arg);
        } else if (md.hasMethodReferenceBody()) {
            referenceDelegate.visit(md, arg);
        }
        return super.visit(md, arg);
    }
}
```

This eliminates the double AST walk (one per transformer) and replaces it with
a single traversal. The two delegate transformers remain separate classes with
focused responsibilities.

### Remove Premature Optimization

The current pre-check in `Preprocessor.process()`:

```java
boolean hasExpressionBodies = cu.findAll(...).stream().anyMatch(...);
boolean hasMethodReferenceBodies = cu.findAll(...).stream().anyMatch(...);
if (!hasExpressionBodies && !hasMethodReferenceBodies) {
    Files.copy(sourceFile, targetFile, REPLACE_EXISTING);
    return;
}
```

This performs two full AST scans before transformation begins — an unmeasured
optimization. It is replaced by the round-based loop where an empty round
(no `changed` flag set) naturally indicates no work was done.

### Round-Based Processing Loop

```java
public void process(Path relativeSourceFile) throws IOException {
    // ... parse ...

    boolean changed;
    do {
        changed = false;
        changed |= conciseMethodTransformer.transform(cu);
        changed |= wildcardExpander.expand(cu);  // Phase 3
    } while (changed);

    if (anyRoundProducedChanges) {
        Files.writeString(targetFile, cu.toString());
    } else {
        Files.copy(sourceFile, targetFile, REPLACE_EXISTING);
    }
}
```

The pass-through behavior (byte-for-byte copy for unmodified files) is preserved
by tracking whether any round produced changes across the entire loop.

### Round Order

Within each round, order is significant:

```
Step 1: ConciseMethodTransformer  (-> and = forms → standard BlockStmt)
Step 2: WildcardExpander          (generates new = forms for missing methods)
```

The concise transformer runs first so the wildcard expander sees all methods
in their expanded form and correctly identifies overrides.

## Wildcard Expander Design

### Input

A wildcard delegation statement in the class body:

```java
List::* = delegate::*;
```

### Processing

1. Resolve the left-side type(s) — discover all methods
2. Determine which methods are already overridden in the class
3. For each unoverridden method, generate a `=` form delegation
4. The generated `=` forms are expanded by `ConciseMethodTransformer` in the next round

### Method Signature Matching

The delegation target is matched by **method signature compatibility**:
- Method name matches (or is remapped via right-side specific name)
- Parameter types are compatible
- Return type is assignable

The target field does NOT need to implement the interface. A field with a
matching method signature is sufficient.

### Generated Output

For `List::* = delegate::*;` the expander produces:

```java
public int size() = delegate::size;
public boolean isEmpty() = delegate::isEmpty;
public boolean contains(Object o) = delegate::contains;
// ... for each unoverridden List method
```

These are standard `=` form declarations that the existing
`MethodReferenceBodyTransformer` handles in the next round.

## Component Diagram

```
Preprocessor.process()
    │
    ▼ Parse
    │
    ┌─────── Round Loop ───────┐
    │                          │
    │  ConciseMethodTransformer│
    │    ├─ ExpressionBody     │  (-> → standard)
    │    └─ MethodReferenceBody│  (= → standard)
    │                          │
    │  WildcardExpander        │  (generates new = forms)
    │                          │
    └──── until no changes ────┘
    │
    ▼ Write (or copy unchanged)
```
