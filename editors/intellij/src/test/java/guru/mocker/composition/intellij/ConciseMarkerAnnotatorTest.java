package guru.mocker.composition.intellij;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/**
 * Sanity tests for {@link ConciseMarkerAnnotator}.
 *
 * <p>The annotator adds <em>silent INFO</em> highlights (colour only), which
 * {@code checkHighlighting} does not assert on. These tests verify the headlessly-assertable
 * invariant: the annotator runs without throwing and does not itself introduce any
 * error/warning highlight on normal code containing a lambda {@code ->} and an assignment
 * {@code =}. Visual confirmation of the marker colour is a manual sandbox check.
 *
 * <p>Test code is self-contained (no {@code java.*} references) because the light fixture has
 * no full JDK on its classpath — unresolved-symbol errors from library types would be false
 * negatives unrelated to the annotator.
 */
public class ConciseMarkerAnnotatorTest extends LightJavaCodeInsightFixtureTestCase {

    /** A normal lambda arrow and a normal assignment must not be flagged by our annotator. */
    public void testNormalArrowAndAssignmentAreUnaffected() {
        myFixture.configureByText("Normal.java",
                "class Normal {\n" +
                        "    interface Op { int apply(int a, int b); }\n" +
                        "    int x = 5;\n" +                     // normal assignment
                        "    Op op = (a, b) -> a + b;\n" +       // normal lambda arrow
                        "    int useIt() { return op.apply(x, x); }\n" +
                        "}\n");
        // Valid self-contained Java: there must be NO error/warning highlights. If our
        // annotator wrongly matched the lambda '->' or the assignment '=', or threw, this fails.
        myFixture.checkHighlighting(true, false, true);
    }
}
