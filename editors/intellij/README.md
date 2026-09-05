# Java Composition — IntelliJ Plugin

Live-editor support for concise method bodies (JEP 8209434) in IntelliJ IDEA:

```java
public int size()            -> c.size();
static int max(int a, int b) = Math::max;
```

Compilation is handled by the [java-composition](../../README.md) Maven preprocessor. This
plugin provides the in-editor experience:

- colours the `->` / `=` markers,
- suppresses the false errors IntelliJ's Java parser reports on concise bodies,
- makes concise methods count as interface implementations (no false "must implement").

Scope: per-method concise forms (`->`, `=`). Wildcard delegation (`Map::* = store::*`) is a
future (Phase 3) concern.

## Status

Under construction — built fresh per `docs/plan-a2-intellij-build.md`. The proof-of-concept
that validated the approach lives (frozen) in `../intellij-spike-museum/`.

## Build

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew buildPlugin      # produces build/distributions/*.zip
./gradlew runIde           # launches a sandbox IDE with the plugin
./gradlew test             # headless highlighting tests
```

## Install (development)

`Settings | Plugins | ⚙ | Install Plugin from Disk…` → select the built ZIP.
