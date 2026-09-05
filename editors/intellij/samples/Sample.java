import java.util.List;

/**
 * Sample for manual verification of the IntelliJ plugin in a sandbox IDE (./gradlew runIde).
 *
 * Open this file and observe:
 *   - A2.1 (done): the -> and = markers are coloured (keyword colour).
 *   - A2.2 (pending): the red squiggles on the concise bodies are NOT yet suppressed.
 *   - A2.3 (pending): a class that `implements` would still show "must implement".
 *
 * This is a manual sample, not a test fixture (headless tests live under src/test).
 */
class Sample {
    final List<String> c = null;

    public int size()            -> c.size();
    public boolean isEmpty()     -> c.isEmpty();
    static int max(int a, int b) = Math::max;

    // A standard body must remain untouched.
    public int normal() {
        int x = 5;
        return x;
    }
}
