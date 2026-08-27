package guru.mocker.maven.plugin;

import guru.mocker.composition.Preprocessor;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Preprocesses Java source files containing Concise Method Bodies (JEP 8209434)
 * into standard Java that {@code javac} can compile.
 *
 * <p>Walks all compile source roots, processes every file (transforming {@code .java}
 * files through the preprocessor, copying non-Java files as-is), and replaces the
 * original source roots with the generated output directories.
 */
@Mojo(name = "preprocess",
        defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class PreprocessMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/java-composition",
            property = "javaComposition.outputDirectory")
    private File outputDirectory;

    @Parameter(property = "javaComposition.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(defaultValue = "${project.compileClasspathElements}", readonly = true, required = true)
    private List<String> classpathElements;

    private Path projectBasedir;
    private Path baseOutputDir;
    private List<Path> classpath;
    private List<String> sourceRoots;
    private int javaFilesProcessed;
    private int nonJavaFilesCopied;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("java-composition preprocessing skipped.");
            return;
        }

        collectContext();
        List<String> newSourceRoots = processSourceRoots();
        replaceSourceRoots(newSourceRoots);
        logStatistics();
    }

    private void collectContext() {
        projectBasedir = project.getBasedir().toPath();
        baseOutputDir = outputDirectory.toPath();
        classpath = classpathElements.stream().map(Path::of).toList();
        sourceRoots = new ArrayList<>(project.getCompileSourceRoots());
        javaFilesProcessed = 0;
        nonJavaFilesCopied = 0;

        getLog().debug("Classpath: " + classpath.size() + " entries");
    }

    private List<String> processSourceRoots() throws MojoExecutionException {
        List<String> newSourceRoots = new ArrayList<>();
        for (String sourceRoot : sourceRoots) {
            processSourceRoot(sourceRoot).ifPresent(newSourceRoots::add);
        }
        return newSourceRoots;
    }

    private Optional<String> processSourceRoot(String sourceRoot) throws MojoExecutionException {
        Path sourceRootPath = Path.of(sourceRoot);
        if (!Files.isDirectory(sourceRootPath)) {
            getLog().debug("Source root does not exist, skipping: " + sourceRootPath);
            return Optional.empty();
        }

        Path relativeSourceRoot = projectBasedir.relativize(sourceRootPath);
        Path outputRoot = baseOutputDir.resolve(relativeSourceRoot);
        Preprocessor preprocessor = new Preprocessor(sourceRootPath, outputRoot, classpath);

        walkAndProcessFiles(sourceRootPath, outputRoot, preprocessor);
        return Optional.of(outputRoot.toString());
    }

    private void walkAndProcessFiles(Path sourceRoot, Path outputRoot, Preprocessor preprocessor)
            throws MojoExecutionException {
        try {
            forEachFile(sourceRoot, outputRoot, preprocessor);
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Failed to walk source root " + sourceRoot + ": " + e.getMessage(), e);
        } catch (PreprocessingException e) {
            throw new MojoExecutionException(e.getMessage(), e.getCause());
        }
    }

    private void forEachFile(Path sourceRoot, Path outputRoot, Preprocessor preprocessor) throws IOException {
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            files.filter(Files::isRegularFile)
                    .forEach(absoluteFile -> processFile(absoluteFile, sourceRoot, outputRoot, preprocessor));
        }
    }

    private void processFile(Path absoluteFile, Path sourceRoot, Path outputRoot, Preprocessor preprocessor) {
        Path relativeFile = sourceRoot.relativize(absoluteFile);
        if (absoluteFile.toString().endsWith(".java")) {
            processJavaFile(preprocessor, relativeFile, absoluteFile);
        } else {
            copyNonJavaFile(outputRoot, relativeFile, absoluteFile);
        }
    }

    private void processJavaFile(Preprocessor preprocessor, Path relativeFile, Path absoluteFile) {
        try {
            preprocessor.process(relativeFile);
            javaFilesProcessed++;
        } catch (IOException e) {
            throw new PreprocessingException(
                    "Failed to preprocess " + absoluteFile + ": " + e.getMessage(), e);
        } catch (Exception e) {
            throw new PreprocessingException(
                    "Error transforming " + absoluteFile + ": " + e.getMessage()
                            + "\nIf this uses the '= MethodRef;' form, ensure all compile "
                            + "dependencies are declared for type resolution.", e);
        }
    }

    private void copyNonJavaFile(Path outputRoot, Path relativeFile, Path absoluteFile) {
        Path targetFile = outputRoot.resolve(relativeFile);
        try {
            Files.createDirectories(targetFile.getParent());
            Files.copy(absoluteFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            nonJavaFilesCopied++;
        } catch (IOException e) {
            throw new PreprocessingException(
                    "Failed to copy " + absoluteFile + ": " + e.getMessage(), e);
        }
    }

    private void replaceSourceRoots(List<String> newSourceRoots) {
        project.getCompileSourceRoots().clear();
        for (String newRoot : newSourceRoots) {
            project.addCompileSourceRoot(newRoot);
        }
    }

    private void logStatistics() {
        getLog().info("Preprocessed " + javaFilesProcessed + " Java files"
                + (nonJavaFilesCopied > 0 ? ", copied " + nonJavaFilesCopied + " non-Java files" : "")
                + ". Source roots replaced.");
    }

    /**
     * Unchecked exception to propagate errors out of stream forEach lambdas.
     */
    private static class PreprocessingException extends RuntimeException {
        PreprocessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
