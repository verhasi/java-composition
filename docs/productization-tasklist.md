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

- Preprocessor compiled for Java 11 (minimum runtime to run the tool)
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
