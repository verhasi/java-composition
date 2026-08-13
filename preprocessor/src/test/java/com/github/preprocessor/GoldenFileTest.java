package com.github.preprocessor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import guru.mocker.semantic.java.ComparisonResult;
import guru.mocker.semantic.java.SemanticComparator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden file tests for the preprocessor.
 *
 * <p>A parameterized test processes each input golden file, transforms it,
 * and compares the output against the expected golden file using the
 * semantic-java-comparator (AST-level comparison ignoring whitespace,
 * comments, and declaration ordering).
 */
class GoldenFileTest {

    private static final Path GOLDEN_INPUT = Path.of("src/test/resources/golden/input");
    private static final Path GOLDEN_EXPECTED = Path.of("src/test/resources/golden/expected");

    private SemanticComparator comparator;

    @BeforeEach
    void setUp() {
        var config = new ParserConfiguration();
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        comparator = new SemanticComparator(new JavaParser(config));
    }

    static Stream<Arguments> goldenFiles() {
        return Stream.of(
                Arguments.of("com/example/AllCases.java"),
                Arguments.of("com/example/Greeting.java"),
                Arguments.of("com/example/Standard.java"),
                Arguments.of("com/example/MethodRefCases.java"),
                Arguments.of("java/util/Collections.java")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenFiles")
    void transformedOutputIsSemanticEquivalentToExpected(String relativePath, @TempDir Path targetRoot) throws IOException {
        Path relativeFile = Path.of(relativePath);
        Preprocessor preprocessor = new Preprocessor(GOLDEN_INPUT, targetRoot);

        preprocessor.process(relativeFile);

        Path actualFile = targetRoot.resolve(relativeFile);
        Path expectedFile = GOLDEN_EXPECTED.resolve(relativeFile);

        assertTrue(Files.exists(actualFile), "Actual output file should exist");
        assertTrue(Files.exists(expectedFile), "Expected golden file should exist");

        String actual = Files.readString(actualFile);
        String expected = Files.readString(expectedFile);

        ComparisonResult result = comparator.compare(actual, expected);
        assertTrue(result.isEquivalent(),
                relativePath + " — transformation not semantically equivalent: " + result);
    }

    @Test
    void throwExpressionIsNotValidConciseBody() {
        // 'throw' is a statement, not an expression.
        // Using it after -> should fail to parse.
        String code = """
                class Example {
                    void fail() -> throw new UnsupportedOperationException();
                }
                """;

        var transformer = new com.github.preprocessor.transform.ExpressionBodyTransformer();
        assertFalse(transformer.containsExpressionBody(code),
                "throw after -> should not parse as a valid expression body");
    }
}
