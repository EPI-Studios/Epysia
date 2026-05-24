package fr.epistudio.epysia.render.shader;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public final class ShaderLoader {

    private static final String CLASSPATH_ROOT = "shaders/";

    private final Optional<Path> filesystemRoot;

    public ShaderLoader(Optional<Path> filesystemRoot) {
        this.filesystemRoot = filesystemRoot.filter(Files::isDirectory);
    }

    public static ShaderLoader autoDetect() {
        Path candidate = Path.of("src/main/resources/shaders");
        return new ShaderLoader(Optional.of(candidate));
    }

    public boolean canHotReload() {
        return filesystemRoot.isPresent();
    }

    public Optional<Path> filesystemRoot() {
        return filesystemRoot;
    }

    public LoadedShader load(String relativePath) {
        Set<String> dependencies = new LinkedHashSet<>();
        String resolved = resolveIncludes(relativePath, dependencies);
        return new LoadedShader(resolved, java.util.List.copyOf(dependencies));
    }

    private String resolveIncludes(String relativePath, Set<String> alreadyIncluded) {
        if (!alreadyIncluded.add(relativePath)) {
            return "";
        }
        String source = readSource(relativePath);
        StringBuilder builder = new StringBuilder(source.length());
        for (String line : source.split("\\r?\\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#include")) {
                String includePath = extractIncludePath(trimmed);
                builder.append(resolveIncludes(includePath, alreadyIncluded));
                builder.append('\n');
            } else {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private String readSource(String relativePath) {
        Optional<String> fromFile = filesystemRoot.flatMap(root -> readFromFilesystem(root, relativePath));
        return fromFile.orElseGet(() -> readFromClasspath(relativePath));
    }

    private Optional<String> readFromFilesystem(Path root, String relativePath) {
        Path absolute = root.resolve(relativePath);
        if (!Files.isRegularFile(absolute)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(absolute, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read shader " + absolute + ": " + exception.getMessage());
        }
    }

    private String readFromClasspath(String relativePath) {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(CLASSPATH_ROOT + relativePath)) {
            if (stream == null) {
                throw new EpysiaException("Shader not found in filesystem or classpath: " + relativePath);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read shader " + relativePath + ": " + exception.getMessage());
        }
    }

    private static String extractIncludePath(String line) {
        int firstQuote = line.indexOf('"');
        int lastQuote = line.lastIndexOf('"');
        if (firstQuote < 0 || lastQuote <= firstQuote) {
            throw new EpysiaException("Malformed #include directive: " + line);
        }
        return line.substring(firstQuote + 1, lastQuote);
    }
}
