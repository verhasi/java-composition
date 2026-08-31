package guru.mocker.composition.transform;

import com.github.javaparser.JavaParser;
import com.github.javaparser.JavaToken;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Position;
import com.github.javaparser.Problem;
import com.github.javaparser.ProblemResolver;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.UnparsedBlockStatement;
import guru.mocker.composition.ast.ConciseMethodDeclaration;

import java.util.Optional;

/**
 * Stage 2 — recognition, as a {@link ProblemResolver}.
 *
 * <p>The recovery fork records a {@link Problem} at each method body it could not parse and
 * retains the skipped tokens as an {@link UnparsedBlockStatement}. This resolver is asked,
 * one problem at a time: it locates the {@code UnparsedBlockStatement} the problem refers to,
 * and if that body is a concise form ({@code -> expr} or {@code = ref}), parses the payload
 * and replaces the method with a {@link ConciseMethodDeclaration}, returning {@code true}.
 *
 * <p>Returning {@code true} tells the parser (the owner of the problem list) to drop the
 * problem, so a file whose only "errors" are concise bodies parses <em>successfully</em>.
 * A genuinely broken body has no concise marker: this returns {@code false}, the problem
 * survives, and the parse stays unsuccessful — surfacing a real syntax error rather than
 * silently emitting wrong output.
 */
public class ConciseRecognitionProcessor implements ProblemResolver {

    private final JavaParser expressionParser = new JavaParser();

    @Override
    public boolean isProblemResolved(ParseResult<? extends Node> result,
                                     ParserConfiguration configuration,
                                     Problem problem) {
        return result.getResult()
                .flatMap(root -> findUnparsedBodyFor(root, problem))
                .map(this::recognizeAndReplace)
                .orElse(false);
    }

    /**
     * Find the concise-bodied method whose retained body ({@link UnparsedBlockStatement})
     * covers the position this problem points at.
     */
    private Optional<MethodDeclaration> findUnparsedBodyFor(Node root, Problem problem) {
        Position errorPos = problem.getLocation()
                .flatMap(TokenRange::toRange)
                .map(range -> range.begin)
                .orElse(null);
        if (errorPos == null) {
            return Optional.empty();
        }
        return root.findAll(MethodDeclaration.class).stream()
                .filter(method -> method.getBody().orElse(null) instanceof UnparsedBlockStatement)
                .filter(method -> bodyCovers(method, errorPos))
                .findFirst();
    }

    private boolean bodyCovers(MethodDeclaration method, Position errorPos) {
        return method.getBody()
                .flatMap(Node::getRange)
                .map(range -> range.contains(errorPos) || range.begin.equals(errorPos))
                .orElse(false);
    }

    /**
     * Recognize the concise body and replace the method with a {@link ConciseMethodDeclaration}.
     * @return true if it was a concise body and was replaced; false otherwise
     */
    private boolean recognizeAndReplace(MethodDeclaration method) {
        UnparsedBlockStatement unparsed = (UnparsedBlockStatement) method.getBody().get();
        TokenRange range = unparsed.getTokenRange().orElse(null);
        if (range == null) {
            return false;
        }
        Concise concise = parseConcise(range);
        if (concise == null) {
            return false;
        }
        method.replace(buildConciseMethod(method, concise));
        return true;
    }

    /**
     * Scan the token range for the marker ({@code ->} or {@code =}) and collect the
     * payload text up to the terminating {@code ;}. Returns null when neither marker
     * is present (not a concise body).
     */
    private Concise parseConcise(TokenRange range) {
        ConciseMethodDeclaration.Form form = null;
        StringBuilder payload = new StringBuilder();
        for (JavaToken token : range) {
            String text = token.getText();
            if (form == null) {
                if ("->".equals(text)) {
                    form = ConciseMethodDeclaration.Form.ARROW;
                } else if ("=".equals(text)) {
                    form = ConciseMethodDeclaration.Form.METHOD_REF;
                }
                // else: leading ')' / whitespace before the marker — skip
                continue;
            }
            if (";".equals(text)) {
                break;
            }
            payload.append(text);
        }
        if (form == null) {
            return null;
        }
        String expressionText = payload.toString().trim();
        Expression expr = parseExpression(expressionText);
        if (expr == null) {
            return null;
        }
        return new Concise(form, expr);
    }

    private Expression parseExpression(String text) {
        ParseResult<Expression> result = expressionParser.parseExpression(text);
        return result.getResult().orElse(null);
    }

    private ConciseMethodDeclaration buildConciseMethod(MethodDeclaration original, Concise concise) {
        ConciseMethodDeclaration cmd = new ConciseMethodDeclaration(concise.form, concise.expression);
        // Copy the signature from the original method.
        cmd.setModifiers(original.getModifiers());
        cmd.setAnnotations(original.getAnnotations());
        cmd.setTypeParameters(original.getTypeParameters());
        cmd.setType(original.getType());
        cmd.setName(original.getName());
        cmd.setParameters(original.getParameters());
        cmd.setThrownExceptions(original.getThrownExceptions());
        cmd.removeBody();
        return cmd;
    }

    /** Small holder for a recognized concise body. */
    private static final class Concise {
        final ConciseMethodDeclaration.Form form;
        final Expression expression;

        Concise(ConciseMethodDeclaration.Form form, Expression expression) {
            this.form = form;
            this.expression = expression;
        }
    }
}
