package guru.mocker.composition.ast;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;

/**
 * A {@link MethodDeclaration} recognized as having a concise body (JEP 8209434),
 * carrying the parsed body expression and which concise form produced it.
 *
 * <p>This is <em>our</em> intermediate AST type, produced by Stage 2 (recognition)
 * from a method whose body the fork retained as an {@code UnparsedBlockStatement}.
 * Stage 3 (transformation) consumes it and produces a 100% standard
 * {@link MethodDeclaration} with an ordinary block body.
 *
 * <p>It is never produced by a parser — it is built by us from a plain
 * {@link Expression} that a parser returned for the (standard-Java) concise payload.
 */
public class ConciseMethodDeclaration extends MethodDeclaration {

    /** Which concise form the method was written in. */
    public enum Form {
        /** The {@code -> expr;} expression form. */
        ARROW,
        /** The {@code = MethodRef;} method-reference form. */
        METHOD_REF
    }

    private final Form form;
    private final Expression bodyExpression;

    /**
     * @param form           the concise form (arrow or method reference)
     * @param bodyExpression the parsed body expression (standard Java)
     */
    public ConciseMethodDeclaration(Form form, Expression bodyExpression) {
        super();
        this.form = form;
        this.bodyExpression = bodyExpression;
    }

    public Form getForm() {
        return form;
    }

    public Expression getBodyExpression() {
        return bodyExpression;
    }

    /**
     * Build a plain {@link MethodDeclaration} with this method's signature (no body).
     * Stage 3 sets the expanded standard body on the returned node.
     *
     * <p>Why not just {@code setBody(...)} on {@code this} and leave it in the tree?
     * Because the pipeline keys on the concrete type: {@code findAll(
     * ConciseMethodDeclaration.class)} detects pending work and each Stage 3 transformer
     * guards with {@code instanceof ConciseMethodDeclaration}. Replacing the node with a
     * plain {@link MethodDeclaration} erases that identity on expansion, so a
     * {@code ConciseMethodDeclaration} in the tree always means "not yet expanded" and the
     * post-Stage-3 tree is guaranteed to contain none — i.e. 100% stock nodes.
     */
    public MethodDeclaration toStandardMethod() {
        MethodDeclaration standard = new MethodDeclaration();
        standard.setModifiers(getModifiers());
        standard.setAnnotations(getAnnotations());
        standard.setTypeParameters(getTypeParameters());
        standard.setType(getType());
        standard.setName(getName());
        standard.setParameters(getParameters());
        standard.setThrownExceptions(getThrownExceptions());
        standard.removeBody();
        return standard;
    }
}
