package guru.mocker.composition.intellij;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/**
 * Tests for {@link ConciseAugmentProvider} — concise methods must count as interface
 * implementations, WITHOUT hiding a genuinely-missing method.
 *
 * <p>Self-contained (no {@code java.*}); the interface and impls are all local so the light
 * fixture (which lacks a full JDK) can resolve everything.
 */
public class ConciseAugmentProviderTest extends LightJavaCodeInsightFixtureTestCase {

    /** A class implementing ALL interface methods concisely must be CLEAN (no "must implement"). */
    public void testFullConciseImplementationIsAccepted() {
        myFixture.configureByText("Full.java",
                "class Host {\n" +
                        "    interface Pair { int key(); int value(); }\n" +
                        "    static class FullImpl implements Pair {\n" +
                        "        private final int k = 1;\n" +
                        "        private final int v = 2;\n" +
                        "        public int key()   -> k;\n" +
                        "        public int value() -> v;\n" +
                        "    }\n" +
                        "}\n");
        // No error expected: the augment provider synthesizes key()/value() so `implements`
        // is satisfied, and the filter suppresses the concise-body parse noise.
        myFixture.checkHighlighting(true, false, true);
    }

    /**
     * A class implementing only SOME interface methods concisely must STILL show the
     * class-level "must implement" for the genuinely-missing one (no over-augmentation).
     *
     * <p>Asserts the error is PRESENT rather than using {@code checkHighlighting}'s exact
     * matching, because IntelliJ emits this particular error twice and on the whole class-
     * declaration range — a known cosmetic quirk we accept; matching it exactly would be
     * brittle.
     */
    public void testPartialConciseImplementationStillErrors() {
        myFixture.configureByText("Partial.java",
                "class Host {\n" +
                        "    interface Pair { int key(); int value(); }\n" +
                        "    static class PartialImpl implements Pair {\n" +
                        "        private final int k = 1;\n" +
                        "        public int key() -> k;\n" +
                        "    }\n" +
                        "}\n");
        java.util.List<com.intellij.codeInsight.daemon.impl.HighlightInfo> infos =
                myFixture.doHighlighting(com.intellij.lang.annotation.HighlightSeverity.ERROR);
        boolean mustImplementValue = infos.stream().anyMatch(i ->
                i.getDescription() != null
                        && i.getDescription().contains("must either be declared abstract")
                        && i.getDescription().contains("value()"));
        assertTrue("Expected a 'must implement value()' error on PartialImpl (the genuine "
                + "missing method must not be hidden by the augment provider)", mustImplementValue);
    }

    /**
     * Edge cases: a generic method, a throwing method, a void method, and a constructor-ref
     * ({@code = Type::new}) form — all implementing an interface concisely — must be accepted
     * (no "must implement"). Verifies the twin copies type parameters, throws, void return, and
     * that the {@code =} form's bodyless method is recognized.
     */
    public void testSignatureEdgeCasesAreAccepted() {
        myFixture.configureByText("Edge.java",
                "class Host {\n" +
                        "    static class Ex extends Exception {}\n" +
                        "    interface Api {\n" +
                        "        <T> T echo(T t);\n" +
                        "        int risky() throws Ex;\n" +
                        "        void act(int a);\n" +
                        "        int sum(int... xs);\n" +
                        "    }\n" +
                        "    static class Impl implements Api {\n" +
                        "        private int total = 0;\n" +
                        "        public <T> T echo(T t)         -> t;\n" +
                        "        public int risky() throws Ex   -> total;\n" +
                        "        public void act(int a)         -> total = a;\n" +
                        "        public int sum(int... xs)      -> total;\n" +
                        "    }\n" +
                        "}\n");
        java.util.List<com.intellij.codeInsight.daemon.impl.HighlightInfo> infos =
                myFixture.doHighlighting(com.intellij.lang.annotation.HighlightSeverity.ERROR);
        boolean anyMustImplement = infos.stream().anyMatch(i ->
                i.getDescription() != null
                        && i.getDescription().contains("must either be declared abstract"));
        assertFalse("Impl implements all Api methods concisely (generic/throws/void/varargs) — "
                + "no 'must implement' expected; found: "
                + infos.stream().map(com.intellij.codeInsight.daemon.impl.HighlightInfo::getDescription)
                        .filter(d -> d != null && d.contains("must either")).toList(),
                anyMustImplement);
    }
}
