# Java Composition — IntelliJ Plugin

Live-editor support for concise method bodies (JEP 8209434) in IntelliJ IDEA:

```java
public int size()            -> c.size();
static int max(int a, int b) = Math::max;
```

Compilation is handled by the [java-composition](../../README.md) Maven preprocessor. This
plugin provides the in-editor experience so concise sources look and behave correctly:

- **Highlights** the `->` / `=` markers (keyword colour), scoped to method-body position.
- **Suppresses** the false errors IntelliJ's Java parser reports on concise bodies — the
  marker parse errors, the "cannot resolve" on a field used in a `= field::m` reference, and
  the trailing `;` noise.
- **Recognizes** concise methods as real implementations, so a class that `implements` an
  interface using concise bodies does not show a false "must implement …" error — while a
  genuinely-missing method is still reported.

## Scope

Per-method concise forms: the expression form `-> expr;` and the method-reference form
`= ref;`. **Wildcard delegation** (`Map::* = store::*`) is **not** supported yet (planned for
a later phase); files using it will still show standard "must implement" errors.

## Install (from disk)

1. Build the plugin ZIP:
   ```sh
   export JAVA_HOME=$(/usr/libexec/java_home -v 21)
   ./gradlew buildPlugin        # → build/distributions/*.zip
   ```
   (Or download a release ZIP from GitHub.)
2. In IntelliJ: **Settings | Plugins | ⚙ | Install Plugin from Disk…** → select the ZIP.
3. **Restart the IDE.** The plugin registers a `PsiAugmentProvider` and is not dynamically
   unloadable, so a restart is required after install/update (IntelliJ will prompt).

Compatible with IntelliJ IDEA 2024.3 (build 243) and later.

## Make the IDE resolve the preprocessed classes

The plugin fixes how concise sources *look*; the Maven preprocessor produces the actual
compiled classes under `target/generated-sources/…`. So the IDE resolves symbols against the
generated standard Java, add the generated directory as a source root via
`build-helper-maven-plugin` in your project's `pom.xml`:

```xml
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>build-helper-maven-plugin</artifactId>
  <executions>
    <execution>
      <id>add-generated-sources</id>
      <phase>generate-sources</phase>
      <goals><goal>add-source</goal></goals>
      <configuration>
        <sources>
          <source>${project.build.directory}/generated-sources/java-composition</source>
        </sources>
      </configuration>
    </execution>
  </executions>
</plugin>
```

After a Maven import, IntelliJ marks that folder as a Generated Sources Root.

## Develop

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew runIde     # sandbox IDE with the plugin (open samples/Sample.java)
./gradlew test       # headless highlighting/augment tests
./gradlew buildPlugin # the distributable ZIP
```

`samples/` holds manual sandbox examples: `Sample.java` (per-method forms) and
`AugmentSample.java` (interface-implementation, full vs. partial). The proof-of-concept that
validated the approach is frozen in `../intellij-spike-museum/`.

## Known cosmetic quirks

- On a class that only *partially* implements an interface, IntelliJ may list the same
  "must implement …" method more than once (a platform double-emission), and a method declared
  in several implemented interfaces is listed per-interface. The Implement-Methods popup shows
  each once, correctly. This does not affect correctness.
