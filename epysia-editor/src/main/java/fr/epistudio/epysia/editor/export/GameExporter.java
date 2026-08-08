package fr.epistudio.epysia.editor.export;

import fr.epistudio.epysia.editor.BuildInfo;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.project.ProjectLibraries;
import fr.epistudio.epysia.project.ProjectStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class GameExporter {

    private static final String TEMPLATE_LAUNCHER_NAME = "EpysiaGame";
    private static final String CONTENT_DIRECTORY = "content";
    private static final String ENGINE_JAR_NAME = "epysia-engine.jar";
    private static final String SCRIPTS_OUTPUT = ".epysia/scripts-out";
    private static final Set<String> EXCLUDED_PROJECT_DIRECTORIES =
            Set.of("build", ".gradle", ".git", ".idea", "target", ".worktrees", "scripts");
    private static final Set<String> EXCLUDED_PROJECT_FILES =
            Set.of("build.gradle", "settings.gradle", "gradle.properties", "gradlew", "gradlew.bat");

    private final Project project;
    private final BuildInfo buildInfo;
    private final GameTemplateRepository templates;

    public GameExporter(Project project) {
        this(project, BuildInfo.load(), new GameTemplateRepository());
    }

    public GameExporter(Project project, BuildInfo buildInfo, GameTemplateRepository templates) {
        this.project = project;
        this.buildInfo = buildInfo;
        this.templates = templates;
    }

    public Path export(ExportRequest request, ExportProgress progress) throws IOException {
        requireOutsideProject(request.outputDirectory());
        Path template = templates.resolve(request.platform(), buildInfo.version(), buildInfo.repository(), progress);
        String name = sanitizeFileName(request.title());
        Path gameRoot = request.outputDirectory().resolve(name);
        copyDirectory(template, gameRoot, progress, ExportStage.COPYING_TEMPLATE);
        TemplateLayout layout = request.platform().layout(gameRoot, TEMPLATE_LAUNCHER_NAME);
        refreshEngineJar(layout.applicationDirectory(), request.platform());
        injectContent(layout.applicationDirectory().resolve(CONTENT_DIRECTORY), progress);
        Optional<String> icon = installIcon(request, layout, gameRoot, name);
        finalizeLauncher(request, layout, name, icon);
        progress.report(ExportStage.WRITING_LAUNCHER, 1.0f);
        archive(gameRoot, request.platform(), name, progress);
        return gameRoot;
    }

    private void requireOutsideProject(Path outputDirectory) throws IOException {
        if (outputDirectory.toAbsolutePath().startsWith(project.rootDirectory().toAbsolutePath())) {
            throw new IOException("Choose an output directory outside the project.");
        }
    }

    private void injectContent(Path content, ExportProgress progress) throws IOException {
        copyProjectContent(content, progress);
        Files.createDirectories(content.resolve(SCRIPTS_OUTPUT));
        verifyLibraries(content);
    }

    private void verifyLibraries(Path content) throws IOException {
        int expected = ProjectLibraries.forProjectRoot(project.rootDirectory()).archives().size();
        int copied = ProjectLibraries.forProjectRoot(content).archives().size();
        if (copied != expected) {
            throw new IOException("Export dropped project libraries: expected " + expected
                    + " jar(s) in libs/, found " + copied + ".");
        }
    }

    private void refreshEngineJar(Path applicationDirectory, TargetPlatform platform) throws IOException {
        if (platform != hostPlatform()) {
            return;
        }
        Path target = applicationDirectory.resolve(ENGINE_JAR_NAME);
        Optional<Path> current = currentEngineJar();
        if (current.isPresent()) {
            Files.createDirectories(applicationDirectory);
            Files.copy(current.get(), target, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        throw new IOException("Cannot find a current epysia-engine.jar to ship. The template's copy is "
                + "from an older build and would fail at launch. Run the engineRuntimeJar task first.");
    }

    static TargetPlatform hostPlatform() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? TargetPlatform.WINDOWS
                : TargetPlatform.LINUX;
    }

    static Optional<Path> currentEngineJar() {
        Path location = engineCodeSource().orElse(null);
        if (location == null) {
            return Optional.empty();
        }
        if (Files.isRegularFile(location) && location.toString().endsWith(".jar")) {
            return Optional.of(location);
        }
        return builtEngineJarNear(location);
    }

    private static Optional<Path> builtEngineJarNear(Path classesDirectory) {
        for (Path parent = classesDirectory; parent != null; parent = parent.getParent()) {
            Path candidate = parent.resolve("libs").resolve(ENGINE_JAR_NAME);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> engineCodeSource() {
        try {
            return Optional.of(Path.of(fr.epistudio.epysia.EpysiaEngine.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI()));
        } catch (RuntimeException | java.net.URISyntaxException ignored) {
            return Optional.empty();
        }
    }

    private void copyProjectContent(Path content, ExportProgress progress) throws IOException {
        Path root = project.rootDirectory();
        Set<String> reachable = ProjectAssetReachability.collect(root);
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> sources = walk.toList();
            for (int index = 0; index < sources.size(); index++) {
                Path source = sources.get(index);
                if (!ProjectAssetReachability.shipsWith(root, source, reachable)) {
                    continue;
                }
                copyProjectPath(root, source, content);
                progress.report(ExportStage.COPYING_PROJECT, (index + 1) / (float) sources.size());
            }
        }
    }

    private static void copyProjectPath(Path root, Path source, Path content) throws IOException {
        Path relative = root.relativize(source);
        if (relative.toString().isEmpty() || isExcluded(relative)) {
            return;
        }
        Path target = content.resolve(relative.toString());
        if (Files.isDirectory(source)) {
            Files.createDirectories(target);
        } else {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static boolean excludesDirectory(String directoryName) {
        return EXCLUDED_PROJECT_DIRECTORIES.contains(directoryName.toLowerCase(Locale.ROOT));
    }

    private static boolean isExcluded(Path relative) {
        String first = relative.getName(0).toString().toLowerCase(Locale.ROOT);
        return EXCLUDED_PROJECT_DIRECTORIES.contains(first)
                || (relative.getNameCount() == 1 && EXCLUDED_PROJECT_FILES.contains(first));
    }


    private Optional<String> installIcon(ExportRequest request, TemplateLayout layout, Path gameRoot, String name)
            throws IOException {
        if (request.iconFile().isEmpty()) {
            return Optional.empty();
        }
        Path source = request.iconFile().get();
        if (!Files.isRegularFile(source)) {
            return Optional.empty();
        }
        String fileName = "icon.png";
        Path content = layout.applicationDirectory().resolve(CONTENT_DIRECTORY);
        Files.createDirectories(content);
        Files.copy(source, content.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        replacePackagedIcon(request, gameRoot, name, source);
        return Optional.of(fileName);
    }

    private void replacePackagedIcon(ExportRequest request, Path gameRoot, String name, Path source)
            throws IOException {
        if (request.platform() != TargetPlatform.LINUX) {
            return;
        }
        Path packaged = gameRoot.resolve("lib").resolve(name + ".png");
        Files.createDirectories(packaged.getParent());
        Files.copy(source, packaged, StandardCopyOption.REPLACE_EXISTING);
        Path templateIcon = gameRoot.resolve("lib").resolve(TEMPLATE_LAUNCHER_NAME + ".png");
        Files.deleteIfExists(templateIcon);
    }

    private void finalizeLauncher(ExportRequest request, TemplateLayout layout, String name,
                                  Optional<String> iconFileName) throws IOException {
        Path targetConfig = layout.config().resolveSibling(name + ".cfg");
        LauncherConfiguration.forGame(request.sceneFileName(), new ProjectStore().readQuality(project),
                        request.gpuPreference(), iconFileName, request.title())
                .writeTo(layout.config(), targetConfig);
        Path targetLauncher = layout.launcher().resolveSibling(name + request.platform().launcherExtension());
        Files.move(layout.launcher(), targetLauncher, StandardCopyOption.REPLACE_EXISTING);
        if (request.platform() == TargetPlatform.LINUX) {
            markExecutable(targetLauncher);
        }
    }

    private static void copyDirectory(Path source, Path destination, ExportProgress progress, ExportStage stage)
            throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            List<Path> paths = walk.toList();
            for (int index = 0; index < paths.size(); index++) {
                copyInto(source, paths.get(index), destination);
                progress.report(stage, (index + 1) / (float) paths.size());
            }
        }
    }

    private static void copyInto(Path source, Path path, Path destination) throws IOException {
        Path target = destination.resolve(source.relativize(path).toString());
        if (Files.isDirectory(path)) {
            Files.createDirectories(target);
        } else {
            Files.createDirectories(target.getParent());
            Files.copy(path, target, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void archive(Path gameRoot, TargetPlatform platform, String name, ExportProgress progress)
            throws IOException {
        Path zip = gameRoot.resolveSibling(name + "-" + platform.identifier() + ".zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip));
             Stream<Path> walk = Files.walk(gameRoot)) {
            List<Path> files = walk.filter(Files::isRegularFile).toList();
            for (int index = 0; index < files.size(); index++) {
                writeZipEntry(output, gameRoot.getParent(), files.get(index));
                progress.report(ExportStage.ARCHIVING, (index + 1) / (float) files.size());
            }
        }
    }

    private static void writeZipEntry(ZipOutputStream output, Path base, Path file) throws IOException {
        output.putNextEntry(new ZipEntry(base.relativize(file).toString().replace('\\', '/')));
        try (InputStream input = Files.newInputStream(file)) {
            input.transferTo(output);
        }
        output.closeEntry();
    }

    private static void markExecutable(Path file) throws IOException {
        try {
            Set<PosixFilePermission> permissions = new HashSet<>(Files.getPosixFilePermissions(file));
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            permissions.add(PosixFilePermission.GROUP_EXECUTE);
            permissions.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(file, permissions);
        } catch (UnsupportedOperationException ignored) {
        }
    }

    private static String sanitizeFileName(String title) {
        String sanitized = title.replaceAll("[\\\\/:*?\"<>|]", "").trim();
        return sanitized.isEmpty() ? "game" : sanitized;
    }
}
