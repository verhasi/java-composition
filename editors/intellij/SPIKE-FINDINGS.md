# IntelliJ Injection Spike — Findings

Status: **scaffold built, awaiting interactive observation.**

The scaffold (Gradle IntelliJ Platform Plugin 2.18.1, IDEA Community 2024.3 + Java) builds
and produces a plugin ZIP. The remaining step is an **interactive sandbox run** to observe
what PSI the Java parser produces for concise bodies — which cannot be done headlessly.

## How to run the spike (interactive)

From `editors/intellij/`:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew runIde
```

This launches a sandbox IntelliJ. Then:

1. Open (or create) a Java file with concise bodies. A ready sample is at
   `editors/intellij/sandbox-samples/Sample.java`:
   ```java
   public int size()            -> c.size();
   static int max(int a, int b) = Math::max;
   ```
2. **Observe highlighting**: are the `->` / `=` markers or the payload colored? Is there a
   red `'{' expected` squiggle? (Screenshot it.)
3. **Run the PSI dump**: `Tools | Dump PSI (Concise Spike)`. Then open the log:
   `Help | Show Log in Finder/Explorer` → `idea.log`. Search for `=== PSI DUMP`.
4. **Read the injector/filter logs**: search `idea.log` for `[spike]` lines — they record
   whether the injector found a `PsiLanguageInjectionHost` and whether the error filter
   matched.

## What to record below (the decision inputs)

Fill these in from the observation:

### 1. What PSI node holds the payload?
- Node class of the payload (`c.size()` / `Math::max`): `______`
- Is it (or a stable ancestor) a `PsiLanguageInjectionHost`?  **YES / NO**
- The `[HOST]` marker in the PSI dump flags injection hosts.

### 2. Shape of the error
- Error element class + description (e.g. `PsiErrorElement "'{' expected"`): `______`
- Its parent node type: `______`
- Did `ConciseErrorFilter` suppress it (look for `[spike] suppressing`)? **YES / NO**

### 3. Did injection attach?
- Did `ConcisePayloadInjector` log `INJECTING` (host found) or `context is NOT a
  PsiLanguageInjectionHost`? `______`
- If injected: does the payload get real Java highlighting/completion inside the fragment?
  **YES / NO**

## Decision (fill after observation)

Per the plan's decision matrix (`docs/plan-intellij-injection-spike.md`):

- [ ] Payload node **is** a host → **injection viable** → A2 = injection (payload) +
      error-filter (squiggle).
- [ ] Payload in a **non-host** `PsiErrorElement` → **injection not viable as-is** → A2 =
      `Annotator` (lexical coloring) + `HighlightErrorFilter`.
- [ ] Mixed/other → describe: `______`

**Recommendation:** `______`
