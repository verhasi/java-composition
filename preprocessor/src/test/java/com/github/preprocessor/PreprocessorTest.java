package com.github.preprocessor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PreprocessorTest {

    private static final Path SOURCE_ROOT = Path.of("src/test/resources/golden/input");

    @Test
    void preprocessorCanBeInstantiated(@TempDir Path targetRoot) {
        Preprocessor preprocessor = new Preprocessor(SOURCE_ROOT, targetRoot);
        assertNotNull(preprocessor);
        assertEquals(SOURCE_ROOT, preprocessor.getSourceRoot());
        assertEquals(targetRoot, preprocessor.getTargetRoot());
        assertTrue(preprocessor.getClasspath().isEmpty());
    }

    @Test
    void processTransformsConciseFile(@TempDir Path targetRoot) throws IOException {
        Preprocessor preprocessor = new Preprocessor(SOURCE_ROOT, targetRoot);
        Path relativeFile = Path.of("com/example/Greeting.java");

        preprocessor.process(relativeFile);

        Path outputFile = targetRoot.resolve(relativeFile);
        assertTrue(Files.exists(outputFile), "Output file should exist");

        String output = Files.readString(outputFile);
        assertTrue(output.contains("return this.name;"), "getName should be expanded");
        assertTrue(output.contains("return prefix + \" \" + this.name;"), "greet should be expanded");
        assertTrue(output.contains("System.out.println(this.name);"), "print should be expanded");
        assertFalse(output.lines().anyMatch(l -> l.matches(".*\\)\\s*->.*") && !l.contains("return")),
                "No method-level arrow syntax should remain");
    }

    @Test
    void processCopiesStandardFileUnchanged(@TempDir Path targetRoot) throws IOException {
        Preprocessor preprocessor = new Preprocessor(SOURCE_ROOT, targetRoot);
        Path relativeFile = Path.of("com/example/Standard.java");

        preprocessor.process(relativeFile);

        Path sourceFile = SOURCE_ROOT.resolve(relativeFile);
        Path outputFile = targetRoot.resolve(relativeFile);
        assertTrue(Files.exists(outputFile), "Output file should exist");

        byte[] sourceBytes = Files.readAllBytes(sourceFile);
        byte[] outputBytes = Files.readAllBytes(outputFile);
        assertArrayEquals(sourceBytes, outputBytes, "Standard file should be copied byte-for-byte");
    }

    @Test
    void processCreatesTargetDirectories(@TempDir Path targetRoot) throws IOException {
        Preprocessor preprocessor = new Preprocessor(SOURCE_ROOT, targetRoot);
        Path relativeFile = Path.of("com/example/Greeting.java");

        preprocessor.process(relativeFile);

        Path outputDir = targetRoot.resolve("com/example");
        assertTrue(Files.isDirectory(outputDir), "Nested directories should be created");
    }

    @Test
    void processThrowsForMissingSourceFile(@TempDir Path targetRoot) {
        Preprocessor preprocessor = new Preprocessor(SOURCE_ROOT, targetRoot);
        Path relativeFile = Path.of("com/example/NonExistent.java");

        assertThrows(IllegalArgumentException.class, () ->
                preprocessor.process(relativeFile));
    }
}
