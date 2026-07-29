package fr.epistudio.epysia.render.shader;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import fr.epistudio.epysia.assets.AssetLocator;
import fr.epistudio.epysia.assets.AssetScheme;
import fr.epistudio.epysia.assets.AssetUri;

import java.nio.file.Files;
import java.util.function.Supplier;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ShaderLoader {

    private static final String CLASSPATH_ROOT = "shaders/";

    private final Optional<Path> filesystemRoot;
    private Supplier<AssetLocator> locatorSource = () -> null;
    private final Map<String, String> virtualSources = new ConcurrentHashMap<>();

    public ShaderLoader(Optional<Path> filesystemRoot) {
        this.filesystemRoot = filesystemRoot.filter(Files::isDirectory);
    }

    public void putVirtualSource(String shaderPath, String source) {
        virtualSources.put(shaderPath, source);
    }

    public void removeVirtualSource(String shaderPath) {
        virtualSources.remove(shaderPath);
    }

    public boolean hasVirtualSource(String shaderPath) {
        return virtualSources.containsKey(shaderPath);
    }

    public static ShaderLoader autoDetect() {
        Path candidate = Path.of("src/main/resources/shaders");
        return new ShaderLoader(Optional.of(candidate));
    }

    public void useProject(Supplier<AssetLocator> projectLocator) {
        this.locatorSource = projectLocator == null ? () -> null : projectLocator;
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

    private String resolveIncludes(String shaderPath, Set<String> alreadyIncluded) {
        if (!alreadyIncluded.add(shaderPath)) {
            return "";
        }
        String source = readSource(shaderPath);
        StringBuilder builder = new StringBuilder(source.length());
        for (String line : source.split("\\r?\\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#include")) {
                String includePath = resolveIncludeTarget(shaderPath, extractIncludePath(trimmed));
                builder.append(resolveIncludes(includePath, alreadyIncluded));
                builder.append('\n');
            } else {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private static String resolveIncludeTarget(String includingPath, String includePath) {
        Path including = Path.of(includingPath);
        if (!including.isAbsolute() || including.getParent() == null) {
            return includePath;
        }
        Path sibling = including.getParent().resolve(includePath);
        return Files.isRegularFile(sibling) ? sibling.toString() : includePath;
    }

    private Optional<String> resolveProjectPath(String shaderPath) {
        return AssetUri.parse(shaderPath)
                .filter(uri -> uri.scheme() == AssetScheme.PROJECT)
                .flatMap(uri -> Optional.ofNullable(locatorSource.get()).map(active -> active.resolvedPath(uri)));
    }

    private String readSource(String shaderPath) {
        String virtual = virtualSources.get(shaderPath);
        if (virtual != null) {
            return virtual;
        }
        Optional<String> projectPath = resolveProjectPath(shaderPath);
        if (projectPath.isPresent()) {
            return readAbsoluteFile(Path.of(projectPath.get()));
        }
        Path candidate = Path.of(shaderPath);
        if (candidate.isAbsolute()) {
            return readAbsoluteFile(candidate);
        }
        Optional<String> fromFile = filesystemRoot.flatMap(root -> readFromFilesystem(root, shaderPath));
        return fromFile.orElseGet(() -> readFromClasspath(shaderPath));
    }

    private static String readAbsoluteFile(Path absolute) {
        try {
            return Files.readString(absolute, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read shader " + absolute + ": " + exception.getMessage());
        }
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
