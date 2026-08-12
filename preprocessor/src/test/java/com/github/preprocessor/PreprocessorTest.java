package com.github.preprocessor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PreprocessorTest {

    private Preprocessor preprocessor;

    @BeforeEach
    void setUp() {
        preprocessor = new Preprocessor();
    }

    @Test
    void preprocessorCanBeInstantiated() {
        assertNotNull(preprocessor);
    }

    @Test
    void processTransformsConciseFile(@TempDir Path targetRoot) throws IOException {
        Path sourceRoot = Path.of("src/test/resources/golden/input");
        Path relativeFile = Path.of("com/example/Greeting.java");
  
        preprocessor.process(sourceRoot, targetRoot, relativeFile);

        Path outputFile = targetRoot.resolve(relativeFile);
        assertTrue(Files.exists(outputFile), "Output file should exist");

        String output = Files.readString(outputFile);
        // Verify transformations happened
        assertTrue(output.contains("return this.name;"), "getName should be expanded");
        assertTrue(output.contains("return prefix + \" \" + this.name;"), "greet should be expanded");
        assertTrue(output.contains("System.out.println(this.name);"), "print should be expanded");
        // Verify no arrow syntax remains
        assertFalse(output.contains("->"), "No arrow syntax should remain");
        // Verify structure preserved
        assertTrue(output.contains("package com.example;"), "Package should be preserved");
        assertTrue(output.contains("private String name;"), "Field should be preserved");
    }

    @Test
    void processCopiessStandardFileUnchanged(@TempDir Path targetRoot) throws IOException {
        Path sourceRoot = Path.of("src/test/resources/golden/input");
        Path relativeFile = Path.of("com/example/Standard.java");

        preprocessor.process(sourceRoot, targetRoot, relativeFile);

        Path sourceFile = sourceRoot.resolve(relativeFile);
        Path outputFile = targetRoot.resolve(relativeFile);
        assertTrue(Files.exists(outputFile), "Output file should exist");

        // Verify byte-for-byte copy
        byte[] sourceBytes = Files.readAllBytes(sourceFile);
        byte[] outputBytes = Files.readAllBytes(outputFile);
        assertArrayEquals(sourceBytes, outputBytes, "Standard file should be copied byte-for-byte");
    }

    @Test
    void processCreatesTargetDirectories(@TempDir Path targetRoot) throws IOException {
        Path sourceRoot = Path.of("src/test/resources/golden/input");
        Path relativeFile = Path.of("com/example/Greeting.java");

        // The com/example subdirectory should be created automatically
        preprocessor.process(sourceRoot, targetRoot, relativeFile);

        Path outputDir = targetRoot.resolve("com/example");
        assertTrue(Files.isDirectory(outputDir), "Nested directories should be created");
    }

    @Test
    void processThrowsForMissingSourceFile(@TempDir Path targetRoot) {
        Path sourceRoot = Path.of("src/test/resources/golden/input");
        Path relativeFile = Path.of("com/example/NonExistent.java");

        assertThrows(IllegalArgumentException.class, () ->
                preprocessor.process(sourceRoot, targetRoot, relativeFile));
    }
}
