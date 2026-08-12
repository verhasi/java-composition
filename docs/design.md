# Design: Java Source Code Preprocessor — Concise Method Bodies

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Preprocessor API                          │
│  process(sourceRoot, targetRoot, relativeSourceFile)             │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                    ┌───────────▼───────────┐
                    │   File I/O Layer       │
                    │  - Read source file    │
                    │  - Create target dirs  │
                    │  - Write output file   │
                    └───────────┬───────────┘
                                │
              ┌─────────────────▼─────────────────┐
              │       Forked JavaParser            │
              │  (Extended JavaCC grammar)         │
              │  - Parses standard Java            │
              │  - Parses concise method bodies    │
              │  - Produces AST                    │
              └─────────────────┬─────────────────┘
                                │
                    ┌───────────▼───────────┐
                    │   Transformer Layer    │
                    │  ConciseBodyTransformer│
                    │  - Visits AST          │
                    │  - Expands -> form     │
                    │  - (Future: = form)    │
                    └───────────┬───────────┘
                                │
                    ┌───────────▼───────────┐
                    │   Output Layer         │
                    │  - Pretty-print AST    │
                    │  - Or copy unchanged   │
                    └───────────────────────┘
```

## Module Structure

```
java-composition/
├── docs/
│   ├── requirements.md
│   └── design.md
├── javaparser/                  ← Forked JavaParser (v3.28.2)
│   ├── javaparser-core/         ← Modified grammar and AST nodes
│   ├── ...other modules...
│   └── pom.xml
├── preprocessor/                ← Our preprocessor library
│   ├── src/main/java/
│   │   └── com/github/preprocessor/
│   │       ├── Preprocessor.java
│   │       └── transform/
│   │           └── ConciseBodyTransformer.java
│   ├── src/test/java/
│   │   └── com/github/preprocessor/
│   │       ├── PreprocessorTest.java
│   │       └── transform/
│   │           └── ConciseBodyTransformerTest.java
│   ├── src/test/resources/
│   │   └── golden/
│   │       ├── input/           ← Source files with concise syntax
│   │       └── expected/        ← Expected output files
│   └── pom.xml
└── pom.xml                      ← Parent POM
```

## Key Design Decisions

### 1. Forked JavaParser with Grammar Extensions

**Decision:** Fork JavaParser and modify its JavaCC grammar to natively parse concise
method bodies.

**Rationale:**
- JavaParser already handles the full complexity of Java syntax (generics, annotations,
  lambdas, etc.)
- Modifying the grammar is more robust than pre-processing text or using regex
- The AST representation allows clean visitor-based transformation
- JavaParser's pretty-printer can output valid Java from the transformed AST

**Alternative considered:** Pre-parse text transformation (regex-based detection of `->` in
method context, converting to placeholder standard Java before parsing). Rejected due to
fragility with complex cases (generics containing `->`, lambdas in method signatures, etc.)

### 2. Two-Pass Architecture: Parse then Transform

**Decision:** The grammar stores the concise expression in a `conciseBody` field on
`MethodDeclaration` without expanding it. A separate visitor pass (`ConciseBodyTransformer`)
then expands concise bodies into standard `BlockStmt` bodies.

**Rationale:**
- **Extensibility:** Future syntax transformations can operate on the concise AST
  representation before the final expansion. For example, a new syntax form could be
  transformed into the concise `->` form by an earlier pass, then expanded to standard
  Java by the existing `ConciseBodyTransformer`.
- **Separation of concerns:** Parsing and transformation are independent stages.
- **Testability:** Each stage can be tested independently — parsing correctness and
  transformation correctness are separate assertions.

**Alternative rejected:** Expanding `-> expr;` directly in the grammar (single-pass).
While simpler, this prevents intermediate transformations that produce concise bodies
as output.

### 3. Pass-Through for Unchanged Files

**Decision:** Files without concise method bodies are copied byte-for-byte, not
parsed-and-reprinted.

**Rationale:**
- Parsing and pretty-printing can alter formatting, whitespace, and comments
- Byte-for-byte copy guarantees no unintended changes to files that don't use the feature
- Simplifies debugging: if a file wasn't modified, it's identical to the original

### 4. AST Representation of Concise Bodies

**Decision:** Extend `MethodDeclaration` with an optional `conciseBody` field (an
`Expression` node) and a boolean flag `isConciseBody`.

**Rationale:**
- Minimal change to existing AST structure
- The transformer can check `isConciseBody` and access the expression directly
- After transformation, the field is cleared and a standard `BlockStmt` body is set
- This approach is also extensible for the `=` form (add `methodReferenceBody` field)

### 5. Detection of Concise Syntax Presence

**Decision:** After parsing, walk the AST to check if any `MethodDeclaration` has
`isConciseBody == true`. If none found, copy file unchanged.

**Rationale:**
- Avoids unnecessary pretty-printing of files that happen to parse successfully but
  don't use the feature
- The check is O(n) in the number of method declarations, which is fast

## Grammar Extension Design

The JavaCC grammar for method declarations currently looks approximately like:

```
MethodDeclaration :=
    Type Identifier FormalParameters [Throws] MethodBody

MethodBody :=
    Block | ";"
```

We extend `MethodBody` to:

```
MethodBody :=
    Block | ";" | "->" Expression ";"
```

When the parser encounters `->` after the method signature (formal parameters and optional
throws clause), it parses the following expression and stores it in the `conciseBody` field
of `MethodDeclaration`. The expression is NOT expanded at parse time — that is done by
the `ConciseBodyTransformer` visitor in a subsequent pass.

**AST representation:** The `MethodDeclaration` class has a new optional field:
```java
@OptionalProperty
private Expression conciseBody;
```

With getter/setter: `getConciseBody()`, `setConciseBody(Expression)`, `hasConciseBody()`.

**Disambiguation:** The `->` token already exists in Java for lambda expressions. However,
in the method declaration context, after the formal parameters and optional throws clause,
the grammar expects either `{` (block), `;` (abstract/native), or our new `->`. There is
no ambiguity because lambda `->` only appears inside expressions, not at the method body
level.

## Transformer Design

The transformer is a `ModifierVisitor<Void>` that walks the AST after parsing:

```java
public class ConciseBodyTransformer extends ModifierVisitor<Void> {

    @Override
    public Visitable visit(MethodDeclaration md, Void arg) {
        if (md.hasConciseBody()) {
            Expression expr = md.getConciseBody().orElseThrow();
            NodeList<Statement> stmts = new NodeList<>();

            if (md.getType() instanceof VoidType) {
                // void method: expression becomes an expression statement
                stmts.add(new ExpressionStmt(expr));
            } else {
                // non-void method: expression becomes return statement
                stmts.add(new ReturnStmt(expr));
            }

            md.setBody(new BlockStmt(stmts));
            md.setConciseBody(null);
        }
        return super.visit(md, arg);
    }
}
```

The transformation is purely syntactic — no type resolution needed. Type correctness is
the programmer's responsibility; `javac` validates after transformation.

## File I/O Strategy

```java
public class Preprocessor {

    public void process(Path sourceRoot, Path targetRoot, Path relativeSourceFile) {
        Path sourceFile = sourceRoot.resolve(relativeSourceFile);
        Path targetFile = targetRoot.resolve(relativeSourceFile);

        // Ensure target directory exists
        Files.createDirectories(targetFile.getParent());

        // Parse with forked JavaParser
        CompilationUnit cu = parse(sourceFile);

        // Check if any concise bodies exist
        if (hasConciseBodies(cu)) {
            // Transform and write
            new ConciseBodyTransformer().visit(cu, null);
            Files.writeString(targetFile, cu.toString());
        } else {
            // Copy unchanged
            Files.copy(sourceFile, targetFile, REPLACE_EXISTING);
        }
    }
}
```

## Extensibility for Phase 2 (`=` Form)

The architecture supports adding the `=` form by:

1. **Grammar:** Add `"=" MethodReference ";"` as another alternative in `MethodBody`
2. **AST:** Add `methodReferenceBody` field to `MethodDeclaration`
3. **Transformer:** Add a new visitor or extend `ConciseBodyTransformer` to handle `=` form
4. **Type Resolution:** The `=` form transformer will need access to type information
   (JavaParser's Symbol Solver or a custom resolution strategy)

The `Preprocessor` class and file I/O layer remain unchanged — only the transformer
internals grow.

## Testing Strategy

### Unit Tests
- Parse concise method bodies and verify AST stores `conciseBody` expression
- Transform individual method declarations and verify expanded output

### Integration Tests (Golden Files)
- Input files in `src/test/resources/golden/input/`
- Expected output files in `src/test/resources/golden/expected/`
- Test reads input, runs preprocessor, compares output to expected file using
  **semantic-java-comparator** (`guru.mocker:comparator-core`) which performs
  AST-level comparison ignoring whitespace, comments, and declaration ordering
- Covers edge cases: annotations, generics, throws, void/non-void, switch expressions,
  mixed files

### Build Verification
- The forked JavaParser must pass its own test suite after grammar modifications
- The preprocessor module must compile against the forked JavaParser
