package com.github.preprocessor;

import com.github.preprocessor.transform.ExpressionBodyTransformer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Java source code preprocessor that transforms Concise Method Bodies
 * (JEP 8209434) into standard Java method bodies.
 *
 * <p>Phase 1 supports the single expression form ({@code ->}).
 * Phase 2 adds the method reference form ({@code =}).
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Preprocessor preprocessor = new Preprocessor(
 *     Path.of("src/main/java"),           // source root
 *     Path.of("target/generated"),         // target root
 *     List.of(Path.of("lib/dep.jar"))      // classpath for type resolution
 * );
 * preprocessor.process(Path.of("com/example/MyClass.java"));
 * }</pre>
 *
 * <p>If the source file contains concise method bodies, the output is the
 * transformed standard Java source. If not, the file is copied unchanged.
 */
public class Preprocessor {

    private final Path sourceRoot;
    private final Path targetRoot;
    private final List<Path> classpath;
    private final ExpressionBodyTransformer expressionBodyTransformer;

    /**
     * Creates a preprocessor for the given source tree.
     *
     * @param sourceRoot root directory of the source tree
     * @param targetRoot root directory for output files
     * @param classpath  list of directories and JAR files for type resolution
     */
    public Preprocessor(Path sourceRoot, Path targetRoot, List<Path> classpath) {
        this.sourceRoot = sourceRoot;
        this.targetRoot = targetRoot;
        this.classpath = List.copyOf(classpath);
        this.expressionBodyTransformer = new ExpressionBodyTransformer();
    }

    /**
     * Creates a preprocessor with a classpath string (split on system path separator).
     *
     * @param sourceRoot      root directory of the source tree
     * @param targetRoot      root directory for output files
     * @param classpathString classpath string (directories and JARs separated by system path separator)
     */
    public Preprocessor(Path sourceRoot, Path targetRoot, String classpathString) {
        this(sourceRoot, targetRoot, parseClasspath(classpathString));
    }

    /**
     * Creates a preprocessor with no classpath (sufficient for {@code ->} form only).
     *
     * @param sourceRoot root directory of the source tree
     * @param targetRoot root directory for output files
     */
    public Preprocessor(Path sourceRoot, Path targetRoot) {
        this(sourceRoot, targetRoot, List.of());
    }

    /**
     * Process a single source file, transforming concise method bodies
     * into standard Java and writing the result to the target directory.
     *
     * <p>If the source file contains no concise method bodies, it is
     * copied unchanged (byte-for-byte) to the target location.
     *
     * @param relativeSourceFile path of the source file relative to sourceRoot
     * @throws IOException              if file reading or writing fails
     * @throws IllegalArgumentException if the source file does not exist
     */
    public void process(Path relativeSourceFile) throws IOException {
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
        Optional<String> transformed = expressionBodyTransformer.transformIfNeeded(source);

        if (transformed.isPresent()) {
            // Write transformed output
            Files.writeString(targetFile, transformed.get());
        } else {
            // Copy unchanged
            Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Path getSourceRoot() {
        return sourceRoot;
    }

    public Path getTargetRoot() {
        return targetRoot;
    }

    public List<Path> getClasspath() {
        return classpath;
    }

    private static List<Path> parseClasspath(String classpathString) {
        if (classpathString == null || classpathString.isBlank()) {
            return List.of();
        }
        return Arrays.stream(classpathString.split(File.pathSeparator))
                .map(Path::of)
                .collect(Collectors.toUnmodifiableList());
    }
}
