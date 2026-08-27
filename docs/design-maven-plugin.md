# Design: java-composition-maven-plugin

## Overview

A Maven plugin that integrates the java-composition preprocessor into the build lifecycle,
transparently transforming concise method bodies into standard Java before compilation.

## Module Coordinates

- **GroupId**: `guru.mocker.composition`
- **ArtifactId**: `java-composition-maven-plugin`
- **Version**: `0.1.0-SNAPSHOT`
- **Packaging**: `maven-plugin`
- **Parent**: `guru.mocker:parent:1.0.20`
- **Package**: `guru.mocker.maven.plugin`
- **Goal prefix**: `java-composition` (auto-derived from artifact name)

## Mojo Design

### Goal: `preprocess`

```java
@Mojo(name = "preprocess",
      defaultPhase = LifecyclePhase.GENERATE_SOURCES,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class PreprocessMojo extends AbstractMojo
```

### Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `project` | `${project}` (readonly) | The Maven project |
| `outputDirectory` | `${project.build.directory}/generated-sources/java-composition` | Base output directory |
| `skip` | `false` | Skip preprocessing |
| `classpathElements` | `${project.compileClasspathElements}` (readonly) | Resolved compile classpath |

### Execution Flow

1. If `skip` is true, log and return.
2. Get compile source roots from `project.getCompileSourceRoots()`.
3. For each source root:
   a. Walk the entire directory tree (all files, not just `.java`).
   b. Compute the output root: `outputDirectory/sourceRootPath`
      (e.g., `target/generated-sources/java-composition/src/main/java`).
   c. For each file:
      - If `.java`: run through the `Preprocessor` (transforms or copies unchanged).
      - If not `.java`: copy as-is to the output tree.
4. Replace compile source roots:
   - Remove the original source root.
   - Add the corresponding output root.
5. Log summary: "Preprocessed N files (M transformed, K unchanged), copied L non-Java files".

### Source Root Mapping

The output preserves the source root path to avoid collisions between multiple source roots:

```
Source root:  src/main/java/com/example/Foo.java
Output:       target/generated-sources/java-composition/src/main/java/com/example/Foo.java

Source root:  target/generated-sources/other-plugin/com/example/Bar.java
Output:       target/generated-sources/java-composition/target/generated-sources/other-plugin/com/example/Bar.java
```

Each original source root is replaced by its corresponding output subtree.

### Classpath Auto-Detection

The `requiresDependencyResolution = ResolutionScope.COMPILE` annotation tells Maven to resolve
all compile-scope dependencies before the mojo runs. The `${project.compileClasspathElements}`
expression provides the resolved list of JAR paths and directories. These are converted to
`List<Path>` and passed to the `Preprocessor` constructor for type resolution (needed by the
`= MethodRef;` form).

### Error Handling

- **IOException** during file processing → `MojoExecutionException` with the source file path.
- **Type resolution failure** (missing classpath) → wrap with message:
  "Type resolution failed for [file]. Ensure all compile dependencies are declared.
  The '= MethodRef;' form requires classpath resolution."
- **Nonexistent source root** → skip with debug log (normal for projects without src/main/java).
- **Output directory creation failure** → fail early with clear message.
- **Summary log** at end of execution.

## Dependencies

```xml
<!-- Maven Plugin API (provided by Maven at runtime) -->
<dependency>
    <groupId>org.apache.maven</groupId>
    <artifactId>maven-plugin-api</artifactId>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>org.apache.maven.plugin-tools</groupId>
    <artifactId>maven-plugin-annotations</artifactId>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>org.apache.maven</groupId>
    <artifactId>maven-core</artifactId>
    <scope>provided</scope>
</dependency>

<!-- The shaded preprocessor library -->
<dependency>
    <groupId>guru.mocker.composition</groupId>
    <artifactId>java-composition</artifactId>
</dependency>
```

The shaded JAR relocates all internal dependencies (JavaParser, Guava, etc.) under
`guru.mocker.internal.*`, so no classpath conflicts with consumer projects.

## Testing Strategy

### Integration Test (maven-invoker-plugin)

A single invoker project that validates both failure without the plugin and success with it:

```
src/it/concise-syntax-project/
├── pom.xml                    (project using the plugin)
├── src/main/java/com/example/Demo.java  (concise syntax)
├── prebuild.groovy            (compile WITHOUT plugin → expect failure)
└── verify.groovy              (after full build WITH plugin → expect success)
```

**prebuild.groovy**: Attempts to compile the source directly with `javac` (or a subprocess Maven
compile without the plugin). Asserts that compilation fails, proving the test resources genuinely
contain concise syntax that javac rejects.

**verify.groovy**: After the invoker builds the project (with the plugin active):
1. Asserts `target/generated-sources/java-composition/.../Demo.java` exists and contains
   standard Java (`return` statements, no `-> expr;` syntax).
2. Asserts `target/classes/com/example/Demo.class` exists (compilation succeeded).

This two-phase approach ensures test resources are real concise-syntax Java files and that
the plugin actually transforms them into compilable code.

### Unit Tests

- Test file walking logic with a temp directory containing `.java` and non-`.java` files.
- Test classpath conversion.
- Test skip behavior.

## Implementation Order

1. Module skeleton + POM + empty Mojo → verify plugin descriptor generates
2. Parameters + file walking (all files, not just `.java`)
3. Classpath resolution
4. Invoke Preprocessor + copy non-Java files
5. Replace source roots with output roots
6. Error handling + summary logging
7. Integration test (single invoker project with prebuild assertion)
8. Wire into reactor + consumer usage docs

## Consumer Usage

```xml
<build>
    <plugins>
        <plugin>
            <groupId>guru.mocker.composition</groupId>
            <artifactId>java-composition-maven-plugin</artifactId>
            <version>0.1.0-SNAPSHOT</version>
            <executions>
                <execution>
                    <goals>
                        <goal>preprocess</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

Optional configuration:
```xml
<configuration>
    <skip>true</skip>  <!-- disable preprocessing -->
    <outputDirectory>${project.build.directory}/my-custom-output</outputDirectory>
</configuration>
```
