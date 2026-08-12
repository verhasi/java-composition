package com.github.preprocessor.transform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ConciseBodyTransformerTest {

    private ConciseBodyTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new ConciseBodyTransformer();
    }

    @Test
    void detectsConciseSyntax() {
        String code = """
                class Example {
                    int length(String s) -> s.length();
                }
                """;
        assertTrue(transformer.containsConciseSyntax(code));
    }

    @Test
    void doesNotDetectStandardMethods() {
        String code = """
                class Example {
                    int length(String s) {
                        return s.length();
                    }
                }
                """;
        assertFalse(transformer.containsConciseSyntax(code));
    }

    @Test
    void transformsNonVoidMethod() {
        String code = """
                class Example {
                    int length(String s) -> s.length();
                }
                """;

        Optional<String> result = transformer.transform(code);
        assertTrue(result.isPresent());

        String output = result.get();
        assertTrue(output.contains("return s.length();"), "Should contain return statement");
        assertTrue(output.contains("{"), "Should contain opening brace");
        assertTrue(output.contains("}"), "Should contain closing brace");
    }

    @Test
    void transformsVoidMethod() {
        String code = """
                class Example {
                    void log(String msg) -> System.out.println(msg);
                }
                """;

        Optional<String> result = transformer.transform(code);
        assertTrue(result.isPresent());

        String output = result.get();
        assertTrue(output.contains("System.out.println(msg);"), "Should contain expression statement");
        assertTrue(output.contains("{"), "Should contain opening brace");
        assertFalse(output.contains("return System.out.println"), "Should NOT wrap void in return");
    }

    @Test
    void transformsMethodReturningMethodReference() {
        String code = """
                import java.util.function.Predicate;
                class Example {
                    Predicate<String> test() -> String::isEmpty;
                }
                """;

        Optional<String> result = transformer.transform(code);
        assertTrue(result.isPresent());

        String output = result.get();
        assertTrue(output.contains("return String::isEmpty;"), "Should return the method reference");
    }

    @Test
    void transformReturnsEmptyForStandardCode() {
        String code = """
                class Example {
                    int length(String s) {
                        return s.length();
                    }
                }
                """;

        Optional<String> result = transformer.transform(code);
        assertFalse(result.isPresent(), "Should return empty for standard code");
    }

    @Test
    void transformIfNeededReturnsTransformedForConciseCode() {
        String code = """
                class Example {
                    int length(String s) -> s.length();
                }
                """;

        Optional<String> result = transformer.transformIfNeeded(code);
        assertTrue(result.isPresent(), "Should return transformed code");
        assertTrue(result.get().contains("return s.length();"));
    }

    @Test
    void transformPreservesClassStructure() {
        String code = """
                package com.example;
                
                import java.util.List;
                
                public class MyClass {
                    private String name;
                    
                    String getName() -> this.name;
                    
                    int add(int a, int b) -> a + b;
                    
                    void print(String msg) -> System.out.println(msg);
                }
                """;

        Optional<String> result = transformer.transform(code);
        assertTrue(result.isPresent());

        String output = result.get();
        assertTrue(output.contains("package com.example;"), "Should preserve package");
        assertTrue(output.contains("import java.util.List;"), "Should preserve import");
        assertTrue(output.contains("private String name;"), "Should preserve field");
        assertTrue(output.contains("return this.name;"), "Should expand getName");
        assertTrue(output.contains("return a + b;"), "Should expand add");
        assertTrue(output.contains("System.out.println(msg);"), "Should expand print");
    }
}
