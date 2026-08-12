package com.github.preprocessor;

import com.github.preprocessor.transform.ConciseBodyTransformer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Java source code preprocessor that transforms Concise Method Bodies
 * (JEP 8209434) into standard Java method bodies.
 *
 * <p>Phase 1 supports the single expression form ({@code ->}).
 * Phase 2 will add the method reference form ({@code =}).
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Preprocessor preprocessor = new Preprocessor();
 * preprocessor.process(
 *     Path.of("src/main/java"),      // source root
 *     Path.of("target/generated"),    // target root
 *     Path.of("com/example/MyClass.java")  // relative source file
 * );
 * }</pre>
 *
 * <p>If the source file contains concise method bodies, the output is the
 * transformed standard Java source. If not, the file is copied unchanged.
 */
public class Preprocessor {

    private final ConciseBodyTransformer transformer;

    public Preprocessor() {
        this.transformer = new ConciseBodyTransformer();
    }

    /**
     * Creates a preprocessor with a custom transformer.
     *
     * @param transformer the transformer to use
     */
    public Preprocessor(ConciseBodyTransformer transformer) {
        this.transformer = transformer;
    }

    /**
     * Process a single source file, transforming concise method bodies
     * into standard Java and writing the result to the target directory.
     *
     * <p>If the source file contains no concise method bodies, it is
     * copied unchanged (byte-for-byte) to the target location.
     *
     * @param sourceRoot         root directory of the source tree
     * @param targetRoot         root directory for output files
     * @param relativeSourceFile path of the source file relative to sourceRoot
     * @throws IOException if file reading or writing fails
     * @throws IllegalArgumentException if the source file does not exist
     */
    public void process(Path sourceRoot, Path targetRoot, Path relativeSourceFile) throws IOException {
        Path sourceFile = sourceRoot.resolve(relativeSourceFile);
        Path targetFile = targetRoot.resolve(relativeSourceFile);

        if (!Files.exists(sourceFile)) {
            throw new IllegalArgumentException("Source file does not exist: " + sourceFile);
        }

        // Ensure target directory exists
        Files.createDirectories(targetFile.getParent());

        // Read source file
        String source = Files.readString(sourceFile);

        // Try to transform
        Optional<String> transformed = transformer.transformIfNeeded(source);

        if (transformed.isPresent()) {
            // Write transformed output
            Files.writeString(targetFile, transformed.get());
        } else {
            // Copy unchanged
            Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
