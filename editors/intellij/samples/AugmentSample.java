/**
 * Sample for verifying A2.3 (PsiAugmentProvider) in the sandbox IDE (./gradlew runIde).
 *
 * A local interface with TWO methods, and two classes implementing it concisely:
 *   - FullImpl provides BOTH methods concisely  → must be CLEAN (no "must implement").
 *   - PartialImpl provides only ONE method      → must STILL show "must implement value()"
 *     for the genuinely-missing method (the augment must NOT hide a real error).
 *
 * This is the discriminating test: A2.3 must make concise methods count as implementations,
 * WITHOUT over-augmenting (i.e. without inventing the method the user actually omitted).
 */
class AugmentSample {

    interface Pair {
        int key();
        int value();
    }

    /** Implements BOTH methods concisely → expected CLEAN. */
    static class FullImpl implements Pair {
        private final int k = 1;
        private final int v = 2;
        public int key()   -> k;
        public int value() -> v;
    }

    /** Implements ONLY key() → expected: class-level "must implement value()" STILL shown. */
    static class PartialImpl implements Pair {
        private final int k = 1;
        public int key() -> k;
        // value() deliberately missing — the error here is REAL and must not be suppressed.
    }
}
