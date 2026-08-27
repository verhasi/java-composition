package guru.mocker.composition;

import guru.mocker.internal.javaparser.StaticJavaParser;
import guru.mocker.internal.javaparser.ast.CompilationUnit;
import guru.mocker.internal.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that the forked JavaParser stores concise method bodies (-> form)
 * in the AST without expanding them. The expansion is done by the
 * ConciseBodyTransformer visitor in a separate pass.
 */
class ConciseMethodBodyParsingTest {

    @Test
    void parsesNonVoidConciseMethodBody() {
        String code = """
                class Example {
                    int length(String s) -> s.length();
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(code);
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class).orElseThrow();

        assertEquals("length", method.getNameAsString());
        assertTrue(method.hasExpressionBody(), "Should have expression body");
        assertEquals("s.length()", method.getExpressionBody().orElseThrow().toString());
        // body should be null since it was declared with -> form
        assertFalse(method.getBody().isPresent(), "Standard body should be absent");
    }

    @Test
    void parsesVoidConciseMethodBody() {
        String code = """
                class Example {
                    void log(String msg) -> System.out.println(msg);
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(code);
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class).orElseThrow();

        assertEquals("log", method.getNameAsString());
        assertTrue(method.hasExpressionBody(), "Should have expression body");
        assertEquals("System.out.println(msg)", method.getExpressionBody().orElseThrow().toString());
        assertFalse(method.getBody().isPresent(), "Standard body should be absent");
    }

    @Test
    void parsesMethodWithAnnotationsAndThrows() {
        String code = """
                class Example {
                    @Override
                    public int read() throws java.io.IOException -> input.read();
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(code);
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class).orElseThrow();

        assertEquals("read", method.getNameAsString());
        assertFalse(method.getThrownExceptions().isEmpty());
        assertTrue(method.isAnnotationPresent("Override"));
        assertTrue(method.hasExpressionBody());
        assertEquals("input.read()", method.getExpressionBody().orElseThrow().toString());
    }

    @Test
    void parsesMethodWithGenerics() {
        String code = """
                import java.util.List;
                class Example {
                    <T> T first(List<T> list) -> list.get(0);
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(code);
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class).orElseThrow();

        assertEquals("first", method.getNameAsString());
        assertFalse(method.getTypeParameters().isEmpty());
        assertTrue(method.hasExpressionBody());
        assertEquals("list.get(0)", method.getExpressionBody().orElseThrow().toString());
    }

    @Test
    void parsesMixedRegularAndConciseMethods() {
        String code = """
                class Example {
                    int add(int a, int b) -> a + b;
                    
                    String greet(String name) {
                        return "Hello, " + name;
                    }
                    
                    void close() -> stream.close();
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(code);
        var methods = cu.findAll(MethodDeclaration.class);
        assertEquals(3, methods.size());

        // add method - concise
        assertTrue(methods.get(0).hasExpressionBody());
        assertEquals("a + b", methods.get(0).getExpressionBody().orElseThrow().toString());

        // greet method - regular (has body, no concise)
        assertFalse(methods.get(1).hasExpressionBody());
        assertTrue(methods.get(1).getBody().isPresent());

        // close method - concise void
        assertTrue(methods.get(2).hasExpressionBody());
        assertEquals("stream.close()", methods.get(2).getExpressionBody().orElseThrow().toString());
    }

    @Test
    void parsesMethodReturningMethodReference() {
        String code = """
                import java.util.function.Predicate;
                class Example {
                    Predicate<String> isEmpty() -> String::isEmpty;
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(code);
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class).orElseThrow();

        assertTrue(method.hasExpressionBody());
        assertEquals("String::isEmpty", method.getExpressionBody().orElseThrow().toString());
    }

    @Test
    void standardMethodsStillParseCorrectly() {
        String code = """
                class Example {
                    int compute(int x) {
                        int result = x * 2;
                        return result + 1;
                    }
                    
                    abstract void doSomething();
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(code);
        var methods = cu.findAll(MethodDeclaration.class);
        assertEquals(2, methods.size());

        // Regular method with body
        assertFalse(methods.get(0).hasExpressionBody());
        assertTrue(methods.get(0).getBody().isPresent());
        assertEquals(2, methods.get(0).getBody().get().getStatements().size());

        // Abstract method without body
        assertFalse(methods.get(1).hasExpressionBody());
        assertFalse(methods.get(1).getBody().isPresent());
    }
}
