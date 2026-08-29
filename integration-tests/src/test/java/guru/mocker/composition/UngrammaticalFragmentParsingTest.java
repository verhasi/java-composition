package guru.mocker.composition;

import guru.mocker.internal.javaparser.JavaParser;
import guru.mocker.internal.javaparser.ParseProblemException;
import guru.mocker.internal.javaparser.ParseResult;
import guru.mocker.internal.javaparser.Problem;
import guru.mocker.internal.javaparser.StaticJavaParser;
import guru.mocker.internal.javaparser.TokenRange;
import guru.mocker.internal.javaparser.ast.CompilationUnit;
import guru.mocker.internal.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Learning / characterization test.
 *
 * <p>Documents how JavaParser behaves when a compilation unit contains a part that is
 * perfectly <em>tokenizable</em> (every character forms a legal Java token) but is
 * <em>not grammatical</em> (the token sequence matches no production). This is the exact
 * situation our concise method bodies create — but here we use a deliberately nonsensical
 * snippet instead of concise syntax, to prove the behavior is about grammar in general,
 * not about our specific feature.
 *
 * <p>These tests assert current behavior to ground the "parser recovery / unparsed
 * fragment" design (see docs/design-parser-recovery-spi.md). If a future JavaParser
 * upgrade changes any of this, these tests will flag it.
 */
class UngrammaticalFragmentParsingTest {

    /**
     * A class whose method body is tokenizable garbage: {@code @ @ @} and {@code ??} are
     * all legal Java tokens, but this sequence is not a valid method body. Nothing here
     * is concise syntax — it is simply not grammatical Java.
     */
    private static final String TOKENIZABLE_BUT_UNGRAMMATICAL =
            "class Example {\n" +
            "    int broken() { @ @ @ ?? >>> }\n" +
            "}\n";

    /**
     * A sanity baseline: this snippet contains a character that cannot form a valid token
     * in this position is NOT what we want. Everything below is intentionally tokenizable.
     */
    private static final String VALID_JAVA =
            "class Example {\n" +
            "    int ok() { return 1; }\n" +
            "}\n";

    // ---------------------------------------------------------------------------------
    // Surface 1: StaticJavaParser.parse(...) THROWS (wraps problems in an exception)
    // ---------------------------------------------------------------------------------

    @Test
    void staticParseThrowsOnUngrammaticalButTokenizableInput() {
        // StaticJavaParser is the "give me a CU or throw" convenience surface.
        // The internal parser throws ParseException; the public API surfaces it wrapped
        // in a ParseProblemException.
        ParseProblemException ex = assertThrows(ParseProblemException.class, () ->
                StaticJavaParser.parse(TOKENIZABLE_BUT_UNGRAMMATICAL));

        // The wrapper carries the accumulated problems.
        assertFalse(ex.getProblems().isEmpty(), "Expected at least one problem to be reported");
    }

    @Test
    void staticParseSucceedsOnValidJava() {
        // Control: the same surface returns a CU for grammatical input, no exception.
        CompilationUnit cu = StaticJavaParser.parse(VALID_JAVA);
        assertTrue(cu.getClassByName("Example").isPresent());
    }

    // ---------------------------------------------------------------------------------
    // Surface 2: new JavaParser().parse(...) does NOT throw; returns an unsuccessful
    // ParseResult carrying the problems. This is the surface that exposes the failure
    // as data rather than control flow.
    // ---------------------------------------------------------------------------------

    @Test
    void instanceParseReturnsUnsuccessfulResultWithProblems() {
        ParseResult<CompilationUnit> result =
                new JavaParser().parse(TOKENIZABLE_BUT_UNGRAMMATICAL);

        assertFalse(result.isSuccessful(),
                "Ungrammatical input must not produce a successful parse");

        List<Problem> problems = result.getProblems();
        assertFalse(problems.isEmpty(), "Expected problems to be reported on the result");

        // Document what a Problem carries: a message, and (when tokens are stored) a
        // TokenRange locating the offending region. This TokenRange is the very thing
        // the design proposes to retain as an UnparsedFragment instead of discarding.
        Problem first = problems.get(0);
        assertFalse(first.getMessage().isEmpty(), "Problem should carry a message");

        Optional<TokenRange> location = first.getLocation();
        // Location presence depends on token storage config; we only document, not force.
        location.ifPresent(range -> {
            assertTrue(range.getBegin() != null, "TokenRange begin should be present when located");
            assertTrue(range.getEnd() != null, "TokenRange end should be present when located");
        });
    }

    @Test
    void instanceParseProducesPartialCompilationUnitOnFailure() {
        // The decisive fact for the design, corrected by observation:
        // On an ungrammatical (but tokenizable) parse, the instance surface reports the
        // parse as UNSUCCESSFUL but STILL returns a partial CompilationUnit. JavaParser's
        // recovery keeps going and produces a tree with a hole where the bad tokens were.
        //
        // This is exactly the substrate the design builds on: recovery already yields a
        // (partial) CU. The proposed change is to retain the skipped tokens as an
        // UnparsedFragment node in that hole, rather than discarding them into a Problem.
        ParseResult<CompilationUnit> result =
                new JavaParser().parse(TOKENIZABLE_BUT_UNGRAMMATICAL);

        assertFalse(result.isSuccessful(),
                "Ungrammatical input must be reported as an unsuccessful parse");
        assertTrue(result.getResult().isPresent(),
                "Recovery still produces a partial CompilationUnit (with a hole)");

        // The partial CU retains the well-formed surroundings: the class declaration and
        // the method name survive; only the ungrammatical body region is affected.
        CompilationUnit cu = result.getResult().get();
        assertTrue(cu.getClassByName("Example").isPresent(),
                "Well-formed structure around the failure is preserved in the partial CU");
    }

    @Test
    void instanceParseSucceedsOnValidJava() {
        // Control on the instance surface.
        ParseResult<CompilationUnit> result = new JavaParser().parse(VALID_JAVA);
        assertTrue(result.isSuccessful());
        assertTrue(result.getResult().isPresent());
    }

    // ---------------------------------------------------------------------------------
    // The precise recovery shape: this is the crux the design targets.
    // ---------------------------------------------------------------------------------

    @Test
    void recoveryKeepsMethodButDiscardsBodyTokens() {
        // Observed behavior (characterized, not assumed):
        //   Input body:  { @ @ @ ?? >>> }   (all legal tokens, ungrammatical sequence)
        //   Result:      method "broken" survives with an EMPTY body { }
        //                the skipped tokens are DISCARDED
        //                one Problem is recorded with an exact source location
        //
        // The empty body is precisely the "hole." The design's UnparsedFragment would
        // occupy this hole and retain the skipped tokens, instead of dropping them.
        ParseResult<CompilationUnit> result =
                new JavaParser().parse(TOKENIZABLE_BUT_UNGRAMMATICAL);

        assertFalse(result.isSuccessful());
        CompilationUnit cu = result.getResult().orElseThrow();

        List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
        assertEquals(1, methods.size(), "The method declaration survives recovery");

        MethodDeclaration broken = methods.get(0);
        assertEquals("broken", broken.getNameAsString());
        assertTrue(broken.getBody().isPresent(), "A body is present after recovery");
        assertTrue(broken.getBody().get().getStatements().isEmpty(),
                "The body is EMPTY: the ungrammatical tokens were skipped and discarded");

        // The problem pinpoints the failure location — the same information the design's
        // retention would attach to the fragment node.
        assertEquals(1, result.getProblems().size());
        String message = result.getProblems().get(0).getVerboseMessage();
        assertTrue(message.contains("Parse error"),
                "Problem should describe the parse error; was: " + message);
    }
}
