package com.github.preprocessor.transform;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
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

import java.util.Optional;

/**
 * AST visitor that expands concise method bodies into standard Java method bodies.
 *
 * <p>This visitor transforms methods declared with the {@code ->} (single expression) form
 * into standard brace-delimited method bodies:
 * <ul>
 *   <li>Non-void: {@code T method(...) -> expr;} becomes {@code T method(...) { return expr; }}</li>
 *   <li>Void: {@code void method(...) -> expr;} becomes {@code void method(...) { expr; }}</li>
 * </ul>
 *
 * <p>The transformation is purely syntactic — no type inference is performed.
 * Type correctness is the programmer's responsibility; {@code javac} validates
 * after transformation.
 *
 * <p>This class also provides convenience methods for parsing, detecting, and
 * transforming source code in one step.
 *
 * <h2>Architecture</h2>
 * <p>The forked JavaParser grammar parses concise method bodies and stores the
 * expression in the {@code expressionBody} field of {@code MethodDeclaration}.
 * This visitor then expands those expressions into standard {@code BlockStmt} bodies.
 * This two-pass design allows future transformers to operate on the concise AST
 * representation before expansion (e.g., converting new syntax forms into concise bodies).
 */
public class ExpressionBodyTransformer extends ModifierVisitor<Void> {

    private final JavaParser parser;
    private boolean transformed;

    public ExpressionBodyTransformer() {
        this.parser = new JavaParser(new ParserConfiguration());
    }

    /**
     * Visit a MethodDeclaration and expand its concise body if present.
     */
    @Override
    public Visitable visit(MethodDeclaration md, Void arg) {
        if (md.hasExpressionBody()) {
            Expression expr = md.getExpressionBody().orElseThrow();
            NodeList<Statement> stmts = new NodeList<>();

            if (md.getType() instanceof VoidType) {
                // void method: expression becomes an expression statement
                stmts.add(new ExpressionStmt(expr));
            } else {
                // non-void method: expression becomes a return statement
                stmts.add(new ReturnStmt(expr));
            }

            BlockStmt body = new BlockStmt(stmts);
            md.setBody(body);
            md.setExpressionBody(null);
            transformed = true;
        }
        return super.visit(md, arg);
    }

    /**
     * Parse source code and check if it contains concise method bodies.
     *
     * @param source the Java source code
     * @return true if parsing succeeds and at least one method has a concise body
     */
    public boolean containsExpressionBody(String source) {
        ParseResult<CompilationUnit> result = parser.parse(source);
        if (result.getResult().isPresent()) {
            CompilationUnit cu = result.getResult().get();
            return cu.findAll(MethodDeclaration.class).stream()
                    .anyMatch(MethodDeclaration::hasExpressionBody);
        }
        return false;
    }

    /**
     * Transform source code by parsing and expanding concise method bodies.
     *
     * @param source the Java source code (may contain concise method bodies)
     * @return the transformed source code as standard Java, or empty if parsing fails
     */
    public Optional<String> transform(String source) {
        ParseResult<CompilationUnit> result = parser.parse(source);
        if (result.getResult().isPresent()) {
            CompilationUnit cu = result.getResult().get();
            transformed = false;
            this.visit(cu, null);
            if (transformed) {
                return Optional.of(cu.toString());
            }
            // No concise bodies found — return empty to signal no transformation needed
            return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Transform source code if it contains concise syntax, otherwise return empty.
     *
     * <p>This combines detection and transformation:
     * <ul>
     *   <li>If the source doesn't contain concise syntax, returns empty (caller should copy as-is)</li>
     *   <li>If it does, parses, expands, and returns the standard Java</li>
     * </ul>
     *
     * @param source the Java source code
     * @return the transformed source, or empty if no transformation was needed or parsing failed
     */
    public Optional<String> transformIfNeeded(String source) {
        return transform(source);
    }
}
