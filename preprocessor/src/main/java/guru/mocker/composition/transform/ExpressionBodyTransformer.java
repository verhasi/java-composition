package guru.mocker.composition.transform;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import guru.mocker.composition.ast.ConciseMethodDeclaration;

/**
 * Stage 3 (arrow form) — expands a {@link ConciseMethodDeclaration} in the
 * {@code ARROW} form into a standard {@link MethodDeclaration}:
 * <ul>
 *   <li>Non-void: {@code T m(...) -> expr;} becomes {@code T m(...) { return expr; }}</li>
 *   <li>Void: {@code void m(...) -> expr;} becomes {@code void m(...) { expr; }}</li>
 * </ul>
 *
 * <p>The transformation is purely syntactic — no type inference is performed.
 */
public class ExpressionBodyTransformer extends ModifierVisitor<Void> {

    private boolean transformed;

    @Override
    public Visitable visit(MethodDeclaration md, Void arg) {
        if (md instanceof ConciseMethodDeclaration
                && ((ConciseMethodDeclaration) md).getForm() == ConciseMethodDeclaration.Form.ARROW) {
            ConciseMethodDeclaration cmd = (ConciseMethodDeclaration) md;
            Expression expr = cmd.getBodyExpression();

            NodeList<Statement> stmts = new NodeList<>();
            if (cmd.getType() instanceof VoidType) {
                stmts.add(new ExpressionStmt(expr));
            } else {
                stmts.add(new ReturnStmt(expr));
            }

            MethodDeclaration standard = cmd.toStandardMethod();
            standard.setBody(new BlockStmt(stmts));
            md.replace(standard);
            transformed = true;
            return standard;
        }
        return super.visit(md, arg);
    }

    /**
     * @return true if the last visit transformed any arrow-form concise method
     */
    public boolean hasTransformed() {
        return transformed;
    }

    public void resetTransformed() {
        this.transformed = false;
    }
}
