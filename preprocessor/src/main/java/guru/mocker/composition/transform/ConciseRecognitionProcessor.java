package guru.mocker.composition.transform;

import com.github.javaparser.JavaParser;
import com.github.javaparser.JavaToken;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Processor;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.UnparsedBlockStatement;
import guru.mocker.composition.ast.ConciseMethodDeclaration;

import java.util.List;

/**
 * Stage 2 — recognition.
 *
 * <p>A JavaParser {@link Processor} that runs after parsing. For every
 * {@link MethodDeclaration} whose body was retained as an
 * {@link UnparsedBlockStatement} (because the standard grammar could not parse a
 * concise body), it recognizes the concise form, parses the (standard-Java) payload
 * expression, and replaces the method with a {@link ConciseMethodDeclaration}.
 *
 * <p>This performs recognition only; expansion to a standard body is Stage 3.
 */
public class ConciseRecognitionProcessor extends Processor {

    private final JavaParser expressionParser = new JavaParser();

    @Override
    public void postProcess(ParseResult<? extends Node> result, com.github.javaparser.ParserConfiguration configuration) {
        result.getResult().ifPresent(root ->
                root.findAll(MethodDeclaration.class).forEach(this::recognizeIfConcise));
    }

    private void recognizeIfConcise(MethodDeclaration method) {
        if (!(method.getBody().orElse(null) instanceof UnparsedBlockStatement)) {
            return;
        }
        UnparsedBlockStatement unparsed = (UnparsedBlockStatement) method.getBody().get();
        TokenRange range = unparsed.getTokenRange().orElse(null);
        if (range == null) {
            return;
        }

        Concise concise = parseConcise(range);
        if (concise == null) {
            return;
        }

        ConciseMethodDeclaration replacement = buildConciseMethod(method, concise);
        method.replace(replacement);
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
