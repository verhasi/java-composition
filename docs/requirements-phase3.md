# Requirements: Phase 3 — Wildcard Delegation (`*::* = *::*`)

## Vision

The project name "Java Composition" reflects the ultimate goal: making **composition
over inheritance** a first-class pattern in Java. The wildcard delegation syntax
eliminates the boilerplate of the decorator/delegation pattern — potentially hundreds
of forwarding methods — from a single line.

```java
class SmartCache<K, V> implements Map<K, V>, Closeable {
    private final Map<K, V> store;
    private final Closeable resource;

    // All Map methods delegate to store
    Map::* = store::*;

    // All Closeable methods delegate to resource
    Closeable::* = resource::*;

    // Override just what you need
    public V put(K key, V value) {
        log("put: " + key);
        return store.put(key, value);
    }
}
```

## Layered Architecture

The wildcard form is a **generator** that produces concise method declarations.
The existing pipeline then handles those. Each layer only knows about the one below it:

```
Layer 3:  *::* = *::*                (wildcard delegation — generates = forms)
              │
              ▼
Layer 2:  int size() = delegate::size;    (= method reference form)
              │
              ▼
Layer 1:  int size() -> delegate.size();  (-> expression form)
              │
              ▼
Layer 0:  int size() { return delegate.size(); }  (standard Java)
```

## Syntax: `[Type(s)]::method(s) = target::method(s)`

The four positions in `*::* = *::*` can each be:

### Left side — what to generate (contract)

| Position 1 (Type) | Meaning |
|---|---|
| `*` | All implemented interfaces |
| `List` | Only methods declared in `List` |
| `[List, Serializable]` | Methods from specific interfaces |
| `[List<String>, Map<Integer, Object>]` | With type parameters |

| Position 2 (Method) | Meaning |
|---|---|
| `*` | All methods from the selected interface(s) |
| `size` | Only the method named `size` |
| `[size, isEmpty, contains]` | Specific named methods |

### Right side — where to delegate (implementation)

| Position 3 (Target) | Meaning |
|---|---|
| `delegate` | A specific field |
| `[store, resource]` | Multiple specific fields (matched by method signature) |
| `*` | Auto-discover which field has a matching method |

| Position 4 (Method) | Meaning |
|---|---|
| `*` | Same method name as the left side |
| `specificMethod` | Map to a different method name |
| `[get, put, remove]` | Specific named methods on the target |

### Examples

```java
// All interface methods → single field
*::* = delegate::*;

// Single interface → single field
List::* = items::*;

// Multiple interfaces → single field
[List, Collection]::* = items::*;

// Multiple interfaces → multiple fields (matched by type)
Map::* = store::*;
Closeable::* = resource::*;

// Specific method
List::size = items::size;

// Specific methods from an interface
List::[size, isEmpty] = items::*;
```

## Override Rule

Any method explicitly declared in the class body (in any form — standard, `->`, or `=`)
**overrides** the wildcard-generated delegation. The wildcard expander skips methods
that already exist.

```java
class MyList<T> implements List<T> {
    List::* = delegate::*;   // generates all List methods

    // This overrides the generated add() delegation
    public boolean add(T e) {
        log("adding");
        return delegate.add(e);
    }
}
```

## Multi-Round Processing Model

Processing follows the same pattern as annotation processing (JSR 269): **rounds
continue until an empty round** (no transformations performed).

### Round ordering within each round is significant:

```
Round N:
    Step 1: ExpressionBodyTransformer  (-> → standard BlockStmt)
    Step 2: MethodReferenceBodyTransformer  (= → standard BlockStmt)
    Step 3: WildcardExpander  (generates new = forms for missing methods)
```

### Why order matters:

The wildcard expander must determine which methods are **already overridden** in the
class. To do this, it needs to see all methods as standard method declarations with
bodies. If an override is still in concise form (`->` or `=`), the expander might not
recognize it as "real" and generate a duplicate.

Therefore:
1. **First** — expand all concise forms to standard bodies
2. **Then** — the wildcard expander sees the full picture (all standard methods)
3. **New round** — if the expander generated new `=` forms, they need expanding

### Convergence guarantee:

- Each round reduces the number of unexpanded concise bodies and wildcards
- A round that produces no changes terminates the loop
- Maximum rounds = 2 in practice (round 1: expand existing + generate, round 2: expand generated, round 3: empty)

### Implementation:

```java
boolean changed;
do {
    changed = false;
    changed |= expressionBodyTransformer.transform(cu);
    changed |= methodReferenceBodyTransformer.transform(cu);
    changed |= wildcardExpander.expand(cu);
} while (changed);
```

## Wildcard Expander Requirements

### FR-1: Interface Method Discovery

The expander MUST discover all methods declared in the target interface(s), including:
- Methods declared directly on the interface
- Methods inherited from super-interfaces
- Default methods (these CAN be overridden by delegation)
- NOT: `Object` methods (`equals`, `hashCode`, `toString`) unless explicitly listed

### FR-2: Override Detection

The expander MUST recognize a method as "already overridden" if:
- A method with matching signature exists in the class body with a standard `BlockStmt`
- This is why concise forms must be expanded BEFORE the wildcard expander runs

### FR-3: Method Generation

For each unoverridden method, the expander generates:
```java
ReturnType method(ParamType1 p1, ParamType2 p2) = target::method;
```

This is a concise `=` form which the `MethodReferenceBodyTransformer` will expand
in the next step/round.

### FR-4: Type Resolution

The expander requires full type resolution (same as `=` form) to:
- Discover interface methods (including inherited)
- Match type parameters between the interface and the implementing class
- Generate correct parameter types in the method signatures

### FR-5: Matching Rule

The delegation target does NOT need to implement the referenced interface. Matching
is based on **method signature compatibility**: the target must have a method with
a matching name, compatible parameter types, and an assignable return type.

A wildcard that expands to zero methods (because all methods are already overridden,
or no matching methods exist on the target) is valid — it simply produces nothing.

### FR-6: Conflict Detection

The expander MUST detect and report errors for:
- Multiple wildcards generating the same method with different delegation targets
  (ambiguous — which target should be used?)

## Extensibility

The round-based processing model allows future syntax forms to be added as new
transformers without modifying existing ones:
- Each transformer is independent and idempotent
- New transformers are inserted at the appropriate position in the round
- The loop handles convergence automatically
