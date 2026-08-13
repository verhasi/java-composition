package com.github.preprocessor.transform;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MethodReferenceBodyTransformerTest {

    @Test
    void boundInstanceMethodReference() {
        String code = """
                import java.util.List;
                class C {
                    private List<String> items;
                    public int size() = items::size;
                }
                """;
        CompilationUnit cu = StaticJavaParser.parse(code);
        MethodDeclaration md = cu.findAll(MethodDeclaration.class).get(0);
        assertTrue(md.hasMethodReferenceBody());

        Expression refExpr = md.getMethodReferenceBody().orElseThrow();
        System.out.println("Ref expr class: " + refExpr.getClass().getSimpleName());
        System.out.println("Ref expr: " + refExpr);

        MethodReferenceExpr ref = (MethodReferenceExpr) refExpr;
        System.out.println("Scope class: " + ref.getScope().getClass().getSimpleName());
        System.out.println("Scope: " + ref.getScope());
        System.out.println("Identifier: " + ref.getIdentifier());
        System.out.flush();

        TypeSolver solver = new ReflectionTypeSolver();
        MethodReferenceBodyTransformer transformer = new MethodReferenceBodyTransformer(solver);
        transformer.visit(cu, null);

        assertTrue(transformer.hasTransformed());
        String output = cu.toString();
        System.out.println("Output:\n" + output);
        assertTrue(output.contains("return items.size();"), "Should expand to items.size()");
    }

    @Test
    void constructorReference() {
        String code = """
                import java.util.ArrayList;
                class C {
                    public ArrayList<String> newList() = ArrayList::new;
                }
                """;
        CompilationUnit cu = StaticJavaParser.parse(code);
        TypeSolver solver = new ReflectionTypeSolver();
        MethodReferenceBodyTransformer transformer = new MethodReferenceBodyTransformer(solver);
        transformer.visit(cu, null);

        String output = cu.toString();
        System.out.println("Constructor ref output:\n" + output);
        assertTrue(output.contains("new ArrayList()") || output.contains("new ArrayList<>()"),
                "Should expand to new ArrayList(). Got: " + output);
    }
}
