import java.io.Closeable;
import java.util.List;
import java.util.Map;

/**
 * ⚠️ OUT OF A2 SCOPE — EXPECTED TO SHOW ERRORS.
 * This file uses WILDCARD delegation (Map::* = store::*), which the A2 plugin does NOT support
 * (deferred to Phase 3). The plugin correctly does nothing here: wildcards are not PsiMethods,
 * so no methods are synthesized and IntelliJ's "must implement ..." errors remain (a method in
 * several interfaces, e.g. isEmpty() in Map+List, is listed many times). This is the expected
 * scope boundary, NOT an A2 bug. Kept as a boundary probe.
 *
 * PSI-RECOVERY PROBE — Phase 3 wildcard delegation syntax (docs/requirements-phase3.md).
 *
 * NONE of this is valid standard Java; every wildcard/delegation line will make IntelliJ's
 * Java parser recover. Open in the sandbox IDE and run Tools | Dump PSI (Concise Spike) to
 * see how the parser recovers each form — which nodes it produces, where PsiErrorElements
 * land, and whether any payload sub-parses into usable PSI (as the -> form did).
 *
 * Grouped by syntax family. Each line is annotated with its documented meaning.
 */
class WildcardProbe<K, V> implements Map<K, V>, List<V>, Closeable {

    private final Map<K, V> store = null;
    private final List<V> items = null;
    private final Closeable resource = null;
    private final Object delegate = null;

    // ---- Family A: baseline (already characterized) — per-method = method-ref ----
    public int size() = store::size;

    // ---- Family B: all interfaces → single field ----
    *::* = delegate::*;

    // ---- Family C: single interface → single field ----
    Map::* = store::*;
    List::* = items::*;
    Closeable::* = resource::*;

    // ---- Family D: multiple interfaces (bracket group) → single field ----
    [List, Closeable]::* = items::*;

    // ---- Family E: interfaces with type parameters ----
    [List<V>, Map<K, V>]::* = store::*;

    // ---- Family F: specific single method ----
    List::size = items::size;

    // ---- Family G: specific method list on the left ----
    List::[size, isEmpty] = items::*;

    // ---- Family H: target method rename (right side named) ----
    List::get = items::getOrDefault;

    // ---- Family I: auto-discover target field (right target is *) ----
    Map::* = *::*;

    // ---- Family J: multiple target fields (bracket group on the right target) ----
    Map::* = [store, resource]::*;

    // ---- Family K: right method list ----
    List::[get, set] = items::[get, set];

    // ---- A normal method — must remain untouched by any recovery heuristic ----
    public void close() {
        // standard body
    }
}
