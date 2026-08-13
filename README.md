# Java Composition — Concise Method Bodies for Java Today

**Use concise method bodies in Java 8, 11, 17, 21, and 25 — without waiting for `javac` to support them.**

This project implements a source code preprocessor for [JEP 8209434: Concise Method Bodies](https://openjdk.org/jeps/8209434), a draft language feature proposed by Brian Goetz on **August 13, 2018**. Eight years later — today — you can start using it.

## What It Does

Two new forms of method bodies that eliminate boilerplate in delegation and simple methods:

### Expression Form (`->`)

```java
// Before (standard Java)
public int size() {
    return c.size();
}

// After (concise)
public int size() -> c.size();
```

### Method Reference Form (`=`)

```java
// Before (standard Java)
public int size() {
    return aList.size();
}

// After (concise)
public int size() = aList::size;
```

The preprocessor transforms concise method bodies into standard Java before compilation. Your IDE sees the concise source; `javac` sees the expanded form. Any Java version from 8 onwards.

## Real-World Example

From the JDK's `Collections.UnmodifiableCollection` — 14 delegation methods become one-liners:

```java
static class UnmodifiableCollection<E> implements Collection<E>, Serializable {
    final Collection<? extends E> c;

    public int size()                          -> c.size();
    public boolean isEmpty()                   -> c.isEmpty();
    public boolean contains(Object o)          -> c.contains(o);
    public Object[] toArray()                  -> c.toArray();
    public <T> T[] toArray(T[] a)              -> c.toArray(a);
    public <T> T[] toArray(IntFunction<T[]> f) -> c.toArray(f);
    public String toString()                   -> c.toString();

    public boolean containsAll(Collection<?> coll) -> c.containsAll(coll);

    @Override
    public void forEach(Consumer<? super E> action) -> c.forEach(action);

    @SuppressWarnings("unchecked")
    @Override
    public Spliterator<E> spliterator() -> (Spliterator<E>)c.spliterator();

    @SuppressWarnings("unchecked")
    @Override
    public Stream<E> stream() -> (Stream<E>)c.stream();

    @SuppressWarnings("unchecked")
    @Override
    public Stream<E> parallelStream() -> (Stream<E>)c.parallelStream();

    // Methods that throw stay as standard bodies
    public boolean add(E e) {
        throw new UnsupportedOperationException();
    }
}
```

## The Two Forms

### `->` (Expression Form)

The expression is placed directly in a `return` statement (or as an expression statement for `void` methods). No type inference needed — purely syntactic.

| Return type | Concise | Expanded |
|---|---|---|
| Non-void | `T method(...) -> expr;` | `T method(...) { return expr; }` |
| void | `void method(...) -> expr;` | `void method(...) { expr; }` |

The expression can be anything: method calls, arithmetic, ternary, switch expressions, `new`, casts, lambdas, method references as values.

### `=` (Method Reference Form)

The method reference is **invoked** with the method's parameters mapped to the call. The preprocessor infers which method is being referenced and generates the correct invocation.

| Reference kind | Concise | Expanded |
|---|---|---|
| Bound instance | `int size() = aList::size;` | `return aList.size();` |
| Unbound instance | `boolean isEmpty(String s) = String::isEmpty;` | `return s.isEmpty();` |
| Static | `int max(int a, int b) = Math::max;` | `return Math.max(a, b);` |
| Constructor | `static Foo make(int a, int b) = Foo::new;` | `return new Foo(a, b);` |
| Array creation | `static int[] create(int n) = int[]::new;` | `return new int[n];` |

## API

```java
// For -> form only (no classpath needed)
var preprocessor = new Preprocessor(sourceRoot, targetRoot);
preprocessor.process(Path.of("com/example/MyClass.java"));

// For = form (classpath needed for type resolution)
var preprocessor = new Preprocessor(sourceRoot, targetRoot, List.of(
    Path.of("lib/dependency.jar")
));
preprocessor.process(Path.of("com/example/MyClass.java"));
```

Files without concise syntax are copied unchanged. Files with concise syntax are parsed, transformed, and written as standard Java.

## How It Works

1. A **forked JavaParser** (based on v3.28.2) extends the JavaCC grammar to parse `-> Expression ;` and `= MethodReference ;` as valid method body forms
2. The parser stores concise bodies in dedicated AST fields without expanding them
3. **ExpressionBodyTransformer** expands `->` forms (purely syntactic)
4. **MethodReferenceBodyTransformer** expands `=` forms (uses JavaParser Symbol Solver for static/instance disambiguation)
5. The transformed AST is pretty-printed as standard Java

```
Source .java file (with concise bodies)
    │
    ▼ Parse (forked JavaParser)
    │
    ▼ ExpressionBodyTransformer (-> form)
    │
    ▼ MethodReferenceBodyTransformer (= form + Symbol Solver)
    │
    ▼ Pretty-print
    │
Target .java file (standard Java, any version)
```

## What's Not Supported

- `throw` after `->` or `=` — `throw` is a statement, not an expression
- Constructors with concise bodies — only methods
- Instance initializers and static initializers

These match the JEP specification.

## Building

```bash
# Build the forked JavaParser first
cd javaparser
./mvnw clean install -DskipTests

# Build and test the preprocessor
cd ..
atlas-mvn clean test -pl preprocessor
```

## Project Structure

```
java-composition/
├── docs/                    Design documents and requirements
├── javaparser/              Forked JavaParser (git subtree from upstream)
│   └── javaparser-core/    Modified grammar + AST extensions
├── preprocessor/            The preprocessor library
│   ├── src/main/java/      Preprocessor API and transformers
│   └── src/test/           Golden file tests (semantic comparison)
└── pom.xml                  Parent POM
```

## Roadmap

- [ ] Maven plugin — preprocess sources in the `generate-sources` phase
- [ ] Gradle plugin — same for Gradle builds
- [ ] Shaded JAR — single dependency with relocated JavaParser (no classpath conflicts)
- [ ] Publish to Maven Central under `guru.mocker` coordinates
- [ ] IDE support — IntelliJ/VS Code plugins for syntax highlighting

## Acknowledgments

- [JEP 8209434](https://openjdk.org/jeps/8209434) by Brian Goetz — the language design this implements
- [JavaParser](https://github.com/javaparser/javaparser) — the foundation for parsing and transformation
- [Semantic Java Comparator](https://bitbucket.org/mocker-guru/semantic-java-comparator) — AST-level comparison for golden file testing

## License

Apache License 2.0

---

*Happy 8th birthday, JEP 8209434. Here's your preview implementation.*
