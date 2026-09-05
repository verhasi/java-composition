package guru.mocker.composition.intellij;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/**
 * Safety tests for {@link ConciseHighlightInfoFilter}.
 *
 * <p>The most important invariant: the filter must NOT over-suppress — a genuine syntax error
 * in normal (non-concise) code must still be reported. `checkHighlighting` with expected
 * {@code <error>} markers asserts exactly that.
 *
 * <p>Self-contained code (no {@code java.*}) because the light fixture has no full JDK.
 */
public class ConciseHighlightInfoFilterTest extends LightJavaCodeInsightFixtureTestCase {

    /** A genuine syntax error in a normal method body must STILL be highlighted. */
    public void testGenuineSyntaxErrorIsNotSuppressed() {
        myFixture.configureByText("Broken.java",
                "class Broken {\n" +
                        "    int ok() { return 1; }\n" +
                        "    void bad() { int y =<error descr=\"Expression expected\"> </error>; }\n" +
                        "}\n");
        // If the filter wrongly suppressed this genuine error, the expected <error> marker
        // would be missing and the test fails.
        myFixture.checkHighlighting(true, false, true);
    }

    /** A completely normal class must have no errors and none introduced/removed. */
    public void testNormalClassIsClean() {
        myFixture.configureByText("Clean.java",
                "class Clean {\n" +
                        "    int x = 5;\n" +
                        "    int get() { return x; }\n" +
                        "}\n");
        myFixture.checkHighlighting(true, false, true);
    }

    /**
     * A GENUINE duplicate — two real-bodied methods with the same signature — must STILL be
     * reported. The "already defined" suppression applies only to concise (bodyless) methods,
     * so this genuine one must not be hidden.
     */
    public void testGenuineDuplicateMethodIsNotSuppressed() {
        myFixture.configureByText("Dup.java",
                "class Dup {\n" +
                        "    <error descr=\"'f()' is already defined in 'Dup'\">int f()</error> { return 1; }\n" +
                        "    <error descr=\"'f()' is already defined in 'Dup'\">int f()</error> { return 2; }\n" +
                        "}\n");
        myFixture.checkHighlighting(true, false, true);
    }
}
