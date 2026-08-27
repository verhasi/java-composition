# Productization Tasklist

## Goal

Make java-composition a publicly available open source tool that the Java community
can use today — a "preview" of JEP 8209434 available for Java 8, 11, 17, 21, and 25.

## Tasks

### 1. Shaded JAR

Create a self-contained JAR that embeds the forked JavaParser and Symbol Solver with
relocated packages to avoid classpath conflicts with the official JavaParser.

- Add `maven-shade-plugin` to the preprocessor module
- Relocate `com.github.javaparser` → `guru.mocker.javaparser`
- Verify no classpath conflicts when users also depend on official JavaParser
- Verify the shaded JAR works standalone (no transitive JavaParser dependencies leak)
- Final artifact: `guru.mocker:java-composition:<version>`

### 2. Maven Plugin

Create a Maven plugin that integrates the preprocessor into the build lifecycle.

- New module: `java-composition-maven-plugin`
- Binds to `generate-sources` phase
- Preprocesses `src/main/java` → `target/generated-sources/java-composition`
- Adds generated sources to the compile source roots
- Configuration: classpath (auto-detected from project dependencies), source/target dirs
- Test with a sample project that uses concise method bodies

### 3. Gradle Plugin

Create a Gradle plugin for the same purpose.

- New module or separate repo: `java-composition-gradle-plugin`
- Registers a task that preprocesses before compilation
- Configuration DSL for source dirs and classpath
- Test with a sample Gradle project

### 4. Multi-Version Compatibility

Ensure the tool itself and its output work across Java versions.

- Preprocessor source compiled for Java 8 (runs on any JVM from 8 onwards)
- Remove Java 9+ features from preprocessor source (text blocks, var, instanceof patterns, switch expressions)
- Output is compatible with Java 8+ (no Java 9+ features in generated code)
- Test preprocessing and compiling output with Java 8, 11, 17, 21, 25 targets
- Document supported versions in README

### 5. Publish to Maven Central

Publish under `guru.mocker` coordinates.

- Configure `pom.xml` for Central (SCM, developers, license, description)
- GPG signing for releases
- Sonatype OSSRH staging
- Release process (manual or CI-triggered)
- Artifacts:
  - `guru.mocker:java-composition:<version>` (shaded library)
  - `guru.mocker:java-composition-maven-plugin:<version>` (Maven plugin)

### 6. CI/CD Pipeline

Automated build, test, and release.

- Bitbucket Pipelines or GitHub Actions
- On push: build forked JavaParser, build preprocessor, run all tests
- On tag: publish to Maven Central
- Badge in README for build status

### 7. Multi-Round Processing

Refactor the preprocessor to use the round-based processing model (preparation for Phase 3).

- Replace single-pass transformation with a loop
- Each round: ExpressionBodyTransformer → MethodReferenceBodyTransformer → (future expanders)
- Loop until no changes in a round
- Existing tests must still pass (current behavior = converges in 1 round)

### 8. Error Reporting

Improve error messages for end users.

- Source file name and line number in error messages
- Clear message when type resolution fails (missing classpath entry)
- Suggestions: "Did you forget to add X to the classpath?"
- Non-zero exit code from Maven plugin on failure

### 9. Documentation

Complete documentation for end users.

- README (done ✓)
- Usage guide: Maven plugin configuration examples
- Usage guide: Gradle plugin configuration examples
- Migration guide: converting existing delegation code to concise form
- FAQ: common issues (classpath, IDE support, debugging)
- Javadoc on public API classes

### 10. IDE Support (stretch goal)

Syntax highlighting and error detection in IDEs.

- IntelliJ plugin: custom language injection or file type for `.java` with concise syntax
- VS Code: TextMate grammar extension for `->` and `=` method body highlighting
- Live preview: show expanded form on hover
- Note: full IDE support is complex — syntax highlighting alone is a good start

## Priority Order

| Priority | Task | Rationale |
|----------|------|-----------|
| 1 | Shaded JAR | Foundation for distribution |
| 2 | Maven Plugin | Makes the tool usable in real projects |
| 3 | Publish to Maven Central | Makes it available to the community |
| 4 | CI/CD Pipeline | Sustainable development |
| 5 | Multi-Version Compatibility | Broader audience |
| 6 | Error Reporting | Production readiness |
| 7 | Documentation | User adoption |
| 8 | Multi-Round Processing | Foundation for Phase 3 |
| 9 | Gradle Plugin | Broader build tool support |
| 10 | IDE Support | Developer experience |

---

## Addendum: Decisions on Open Questions (2026-08-26)

### Maven Coordinates

- **GroupId**: `guru.mocker.composition` for all modules
- **Artifacts**:
  - `guru.mocker.composition:java-composition` — shaded preprocessor library
  - `guru.mocker.composition:java-composition-maven-plugin` — Maven plugin
- **Version**: `0.1.0-SNAPSHOT` (first release will be `0.1.0`, functionality not yet at 1.0 level)

### POM Structure

The root POM is a **reactor aggregator only** — it lists modules but is not their parent.
Each module independently inherits from `guru.mocker:parent:1.0.20` for release/deploy
infrastructure (Sonatype, GPG signing, deploy config).

```
java-composition/pom.xml          (aggregator, no parent, packaging=pom)
├── preprocessor/pom.xml           (parent = guru.mocker:parent:1.0.20)
└── maven-plugin/pom.xml           (parent = guru.mocker:parent:1.0.20)
```

The `javaparser/` subtree remains outside the reactor — built separately with `./mvnw`.

### Java Version

- **Preprocessor runtime requirement**: Java 21. The tool itself uses Java 21 features
  (pattern matching, `var`, etc.) and requires Java 21+ to run.
- **Output compatibility**: The generated Java source is compatible with whatever source
  level the consuming project targets (Java 8, 11, 17, 21, 25). The Maven plugin passes
  the project's configured source version to JavaParser's `ParserConfiguration`.
- **No rewrite to Java 8**: The preprocessor source stays at Java 21.

### Maven Plugin Details

- **Package**: `guru.mocker.maven.plugin`
- **Goal prefix**: `java-composition` (auto-derived from artifact name)
- **Source handling**: Processes the project's configured compile source directories
  (typically `src/main/java`). Test sources not in scope for now.
- **Incremental build**: Not needed — full reprocess every invocation.

### Multi-Round Processing

Not needed for the current release. The Maven plugin does not need to account for it.
Can be added later without breaking the plugin interface.

### CI/CD and Release

- **Source**: GitHub (public)
- **Release pipeline**: Mirror to Bitbucket, release via Bitbucket Pipelines
  (reusing existing `guru.mocker` release infrastructure and Sonatype credentials)
- **SCM URL**: Resolved at release time via `${BITBUCKET_GIT_HTTP_ORIGIN}` (same
  pattern as the mixin project)
- **GPG key**: Existing key from other `guru.mocker` releases
