# Design: Phase 2 — Method Reference Form (`=`)

## Overview

Phase 2 adds the method reference form of concise method bodies (JEP 8209434).
While the `->` form (Phase 1) is purely syntactic, the `=` form requires **type resolution**
to determine how to expand a method reference into an invocation.

```java
// Input
public int size() = aList::size;

// Output (expanded)
public int size() {
    return aList.size();
}
```

## Syntax

```
MethodBody :=
    Block | ";" | "->" Expression ";" | "=" MethodReference ";"
```

The `=` sign followed by a method reference and semicolon. The method reference uses
standard Java syntax: `Type::method`, `expr::method`, `Type::new`, `Type[]::new`.

## Semantics: `=` vs `->`

| Form | Meaning | Type resolution needed |
|------|---------|----------------------|
| `T method(...) -> expr;` | Return the expression value | No — purely syntactic |
| `T method(...) = Ref::method;` | Invoke the referenced method | Yes — must resolve reference kind |

Key distinction:
- `Predicate<String> test() -> String::isEmpty;` — **returns** the method reference as a functional interface
- `boolean test(String s) = String::isEmpty;` — **invokes** `s.isEmpty()` and returns the result

## Method Reference Kinds and Expansion Rules

### 1. Bound instance method (`expr::method`)

Receiver is an expression (field access, variable, `this`, `super`).
Parameters of the declaring method are passed as arguments.

```java
public int size() = aList::size;
// → return aList.size();

public T get(int index) = aList::get;
// → return aList.get(index);
```

### 2. Unbound instance method (`Type::instanceMethod`)

The first parameter of the declaring method becomes the receiver.
Remaining parameters are passed as arguments.

```java
boolean isEmpty(String s) = String::isEmpty;
// → return s.isEmpty();

int compare(String a, String b) = String::compareTo;
// → return a.compareTo(b);
```

### 3. Static method (`Type::staticMethod`)

All parameters are passed as arguments to the static method.

```java
int max(int a, int b) = Math::max;
// → return Math.max(a, b);

int parseInt(String s) = Integer::parseInt;
// → return Integer.parseInt(s);
```

### 4. Constructor reference (`Type::new`)

All parameters are passed to the constructor.

```java
static Foo make(int a, int b, int c) = Foo::new;
// → return new Foo(a, b, c);

static List<String> newList() = ArrayList::new;
// → return new ArrayList<>();
```

### 5. Array creation reference (`Type[]::new`)

First parameter is the array size.

```java
static int[] create(int size) = int[]::new;
// → return new int[size];
```

### Void methods

For void return type, the invocation becomes an expression statement (no `return`):

```java
void forEach(Consumer<? super E> action) = c::forEach;
// → c.forEach(action);

void log(String msg) = System.out::println;
// → System.out.println(msg);
```

## Disambiguation: Static vs Unbound Instance

Given `Type::method`, we cannot determine from syntax alone whether `method` is:
- A **static** method on `Type` (all params passed as args)
- An **instance** method on `Type` (first param is receiver, rest are args)

**Resolution**: Use JavaParser's Symbol Solver to inspect `Type` and determine
whether `method` is static or instance. This requires access to:
- Source files being preprocessed (same source tree)
- Compiled classes on the classpath (JDK, libraries)

## Architecture

### Preprocessor API (updated)

```java
// Construction: configure source tree, target, and classpath for resolution
Preprocessor preprocessor = new Preprocessor(sourceRoot, targetRoot, classpath);

// Processing: per-file
preprocessor.process(Path.of("com/example/MyClass.java"));
```

Constructor parameters:
- `sourceRoot` — root of source tree (for Symbol Solver to find source types)
- `targetRoot` — root for output files
- `classpath` — `List<Path>` of directories and JAR files (for Symbol Solver to find compiled types)

Overloaded classpath constructor:
- `new Preprocessor(sourceRoot, targetRoot, classpathString)` — splits on `File.pathSeparator`

### AST Representation

`MethodDeclaration` gets two distinct fields:

```java
@OptionalProperty
private Expression expressionBody;       // -> form (Phase 1, renamed from conciseBody)

@OptionalProperty
private MethodReferenceExpr methodReferenceBody;  // = form (Phase 2)
```

With corresponding accessors:
- `getExpressionBody()`, `setExpressionBody()`, `hasExpressionBody()`
- `getMethodReferenceBody()`, `setMethodReferenceBody()`, `hasMethodReferenceBody()`

### Transformation Pipeline

```
Source file
    │
    ▼ Parse (forked JavaParser)
    │
    AST with expressionBody / methodReferenceBody fields populated
    │
    ▼ ExpressionBodyTransformer (Phase 1 — unchanged logic)
    │
    ▼ MethodReferenceBodyTransformer (Phase 2 — new)
    │   - Resolve method reference kind via Symbol Solver
    │   - Generate correct invocation expression
    │   - Wrap in return/expression statement
    │
    AST with standard BlockStmt bodies
    │
    ▼ Pretty-print
    │
    Output file
```

### MethodReferenceBodyTransformer

```java
public class MethodReferenceBodyTransformer extends ModifierVisitor<Void> {

    private final TypeSolver typeSolver;

    @Override
    public Visitable visit(MethodDeclaration md, Void arg) {
        if (md.hasMethodReferenceBody()) {
            MethodReferenceExpr ref = md.getMethodReferenceBody().get();
            Expression invocation = expandMethodReference(md, ref);

            BlockStmt body = new BlockStmt();
            if (md.getType() instanceof VoidType) {
                body.addStatement(new ExpressionStmt(invocation));
            } else {
                body.addStatement(new ReturnStmt(invocation));
            }

            md.setBody(body);
            md.setMethodReferenceBody(null);
        }
        return super.visit(md, arg);
    }

    private Expression expandMethodReference(MethodDeclaration md, MethodReferenceExpr ref) {
        // 1. Constructor: Type::new → new Type(params...)
        // 2. Array creation: Type[]::new → new Type[param0]
        // 3. Bound instance: expr::method → expr.method(params...)
        // 4. Type::method → resolve static vs instance
        //    - Static: Type.method(params...)
        //    - Instance: param0.method(param1, param2, ...)
    }
}
```

### Symbol Solver Configuration

```java
TypeSolver typeSolver = new CombinedTypeSolver(
    new ReflectionTypeSolver(),                    // JDK classes
    new JavaParserTypeSolver(sourceRoot),           // Source being preprocessed
    new JarTypeSolver(jarPath1),                   // Library JARs
    new JavaParserTypeSolver(libSourcePath)         // Library sources
);
```

## Dependencies

```xml
<dependency>
    <groupId>com.github.javaparser</groupId>
    <artifactId>javaparser-symbol-solver-core</artifactId>
    <version>3.28.2-SNAPSHOT</version>
    <classifier>java-composition</classifier>
</dependency>
```

## Error Handling

The preprocessor operates in **strict** mode:
- If a method reference cannot be resolved → fail with a clear error message
- If the reference target is ambiguous → fail
- No heuristics or best-effort guessing

Errors include:
- Type not found on classpath or in source tree
- Method not found on the resolved type
- Ambiguous overload that can't be resolved from parameter types

## Testing Strategy

Golden file tests covering all method reference kinds:
- Bound instance (`aList::size`, `this::method`, `super::method`)
- Unbound instance (`String::isEmpty`, `String::compareTo`)
- Static (`Math::max`, `Integer::parseInt`)
- Constructor (`Foo::new`, `ArrayList::new`)
- Array creation (`int[]::new`, `String[]::new`)
- Void methods
- Mixed `->` and `=` forms in same file
- Error case: `throw` is not valid after `=` either
