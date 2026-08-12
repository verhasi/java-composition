# Requirements: Java Source Code Preprocessor — Concise Method Bodies

## Overview

This project implements a Java source code preprocessor that transforms Java source files
using the proposed "Concise Method Bodies" syntax (JEP 8209434, draft) into standard Java
source code compilable by the current `javac`. It serves as a demonstration and early
prototype of this potential future language feature.

**Reference:** https://openjdk.org/jeps/8209434

## Scope

### Phase 1 (Current): Single Expression Form (`->`)

The preprocessor handles the **single expression form**, where a method body is replaced
by an arrow (`->`) followed by a single expression and a semicolon:

```java
// Concise form (input)
int length(String s) -> s.length();

// Standard form (output)
int length(String s) {
    return s.length();
}
```

### Phase 2 (Future): Method Reference Form (`=`)

The **method reference form** will be addressed in a future phase:

```java
// Concise form (input)
public int size() = aList::size;

// Standard form (output) — requires type resolution
public int size() {
    return aList.size();
}
```

This form requires semantic analysis (type resolution, method reference expansion) and is
explicitly out of scope for Phase 1.

## Functional Requirements

### FR-1: Source File Transformation

The preprocessor SHALL accept three parameters:
- `sourceRoot` — the root directory of the source tree
- `targetRoot` — the root directory for output files
- `relativeSourceFile` — the path of the source file relative to `sourceRoot`

The output file SHALL be written to `targetRoot / relativeSourceFile`, preserving the
directory structure and file name.

### FR-2: Single Expression Form Transformation Rules

For the `->` form, the transformation is purely syntactic:

| Method Return Type | Concise Form | Expanded Form |
|---|---|---|
| Non-void | `T method(...) -> expr;` | `T method(...) { return expr; }` |
| void | `void method(...) -> expr;` | `void method(...) { expr; }` |

The expression `expr` can be any valid Java expression, including:
- Method calls: `-> s.length();`
- Arithmetic: `-> a + b;`
- Ternary: `-> x > 0 ? x : -x;`
- Switch expressions: `-> switch(d) { case 1 -> "MON"; default -> "?"; };`
- Method references (as values): `-> String::isEmpty;` (returns a functional interface instance)
- Constructor calls: `-> new ArrayList<>();`
- Field access: `-> this.name;`

### FR-3: No Type Inference by Preprocessor

The preprocessor does NOT perform type checking or inference. It performs a purely
syntactic transformation. Any type errors in the concise form will be caught by `javac`
after transformation. For example:

```java
// Invalid: s::isEmpty evaluates to a functional interface, not boolean
boolean isEmpty(String s) -> s::isEmpty;
// Transforms to (javac will reject this):
boolean isEmpty(String s) { return s::isEmpty; }
```

### FR-4: Pass-Through for Unmodified Files

If a source file does not contain any concise method body syntax, it SHALL be copied
unchanged (byte-for-byte) to the target directory.

### FR-5: Method Declaration Context

Concise method bodies SHALL be supported in all contexts where non-abstract, non-native
methods can appear:
- Instance methods in classes
- Static methods in classes
- Default methods in interfaces
- Methods in anonymous classes
- Methods with annotations, generics, `throws` clauses

Concise bodies SHALL NOT apply to:
- Constructors
- Instance initializers
- Static initializers

### FR-6: Preservation of Source Structure

The transformation SHALL preserve:
- Package declarations
- Import statements
- Comments (best effort — depends on parser capabilities)
- Annotations
- Modifiers (public, private, static, final, etc.)
- Generic type parameters
- Throws clauses

## Non-Functional Requirements

### NFR-1: Implementation Language

Java 21 (source and target compatibility).

### NFR-2: Parsing Strategy

Uses a **forked version of JavaParser** (v3.28.2) with JavaCC grammar extensions to
natively parse the concise method body syntax. The fork resides within the project
directory structure.

### NFR-3: Library-Only

The preprocessor is a Java library. No CLI or Maven plugin in this phase. Integration
into build tools is a future concern.

### NFR-4: Testing Strategy

Golden file testing: input `.java` files with concise syntax are transformed and compared
against expected output `.java` files.

### NFR-5: Extensibility

The architecture SHALL allow addition of the `=` (method reference) form without major
refactoring. The transformer design should support multiple transformation strategies.

## Future Considerations: Method Reference Form (`=`)

The `=` form has fundamentally different semantics from the `->` form:

- `->` uses the method's **return type** as the target type for the expression
- `=` uses the method's **parameter types AND return type** to resolve the method reference

The `=` form expansion requires:
1. Resolving which method the reference refers to (overload resolution)
2. Mapping method parameters to invocation arguments (receiver vs. arguments for bound/unbound references)
3. Handling all method reference kinds: static, unbound instance, bound instance, constructor (`::new`), array creation

This will likely require JavaParser's Symbol Solver or equivalent type resolution
infrastructure.

### Examples of `=` form expansion:

```java
// Unbound instance method reference
boolean isEmpty(String s) = String::isEmpty;
// Expands to: boolean isEmpty(String s) { return s.isEmpty(); }

// Static method reference
int max(int a, int b) = Math::max;
// Expands to: int max(int a, int b) { return Math.max(a, b); }

// Constructor reference
public static Foo make(int a, int b) = Foo::new;
// Expands to: public static Foo make(int a, int b) { return new Foo(a, b); }

// Bound instance method reference
public int size() = aList::size;
// Expands to: public int size() { return aList.size(); }
```
