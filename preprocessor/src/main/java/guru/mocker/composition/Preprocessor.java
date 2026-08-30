package guru.mocker.composition;

import guru.mocker.composition.transform.ExpressionBodyTransformer;
import guru.mocker.composition.transform.MethodReferenceBodyTransformer;
import guru.mocker.composition.transform.ConciseRecognitionProcessor;
import guru.mocker.composition.ast.ConciseMethodDeclaration;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
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
    private final MethodReferenceBodyTransformer methodReferenceBodyTransformer;
    private final JavaParser parser;

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

        TypeSolver typeSolver = MethodReferenceBodyTransformer.createTypeSolver(sourceRoot, classpath);
        this.methodReferenceBodyTransformer = new MethodReferenceBodyTransformer(typeSolver);

        ParserConfiguration config = new ParserConfiguration();
        config.setSymbolResolver(new JavaSymbolSolver(typeSolver));
        // Stage 1: retain unparsable concise bodies as UnparsedBlockStatement.
        config.setRetainUnparsedTokens(true);
        // Stage 2: recognize retained bodies as ConciseMethodDeclaration during parsing.
        config.getProcessors().add(ConciseRecognitionProcessor::new);
        this.parser = new JavaParser(config);
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

        // Parse
        ParseResult<CompilationUnit> parseResult = parser.parse(source);
        if (!parseResult.getResult().isPresent()) {
            // Can't parse — copy unchanged
            Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        CompilationUnit cu = parseResult.getResult().get();

        // Stage 2 already ran during parsing (ConciseRecognitionProcessor), so any concise
        // bodies are now ConciseMethodDeclaration nodes. If there are none, copy unchanged.
        boolean hasConcise = !cu.findAll(ConciseMethodDeclaration.class).isEmpty();
        if (!hasConcise) {
            Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        // Stage 3: expand ConciseMethodDeclaration nodes into standard MethodDeclaration.
        // Arrow form (-> expr) is purely syntactic; method-reference form (= ref) resolves
        // via the Symbol Solver.
        expressionBodyTransformer.visit(cu, null);
        methodReferenceBodyTransformer.visit(cu, null);

        // Write transformed output
        Files.writeString(targetFile, cu.toString());
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
