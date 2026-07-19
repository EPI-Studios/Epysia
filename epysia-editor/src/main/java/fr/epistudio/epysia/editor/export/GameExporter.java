package fr.epistudio.epysia.editor.export;

import fr.epistudio.epysia.editor.BuildInfo;
import fr.epistudio.epysia.project.Project;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class GameExporter {

    private static final String TEMPLATE_LAUNCHER_NAME = "EpysiaGame";
    private static final String CONTENT_DIRECTORY = "content";
    private static final String SCRIPTS_OUTPUT = ".epysia/scripts-out";
    private static final Set<String> EXCLUDED_PROJECT_DIRECTORIES =
            Set.of("build", ".gradle", ".git", ".idea", "target", ".worktrees", "scripts");

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

    public Path export(ExportRequest request) throws IOException {
        requireOutsideProject(request.outputDirectory());
        Path template = templates.resolve(request.platform(), buildInfo.version(), buildInfo.repository());
        String name = sanitizeFileName(request.title());
        Path gameRoot = request.outputDirectory().resolve(name);
        copyDirectory(template, gameRoot);
        TemplateLayout layout = request.platform().layout(gameRoot, TEMPLATE_LAUNCHER_NAME);
        injectContent(layout.applicationDirectory().resolve(CONTENT_DIRECTORY));
        finalizeLauncher(request, layout, name);
        archive(gameRoot, request.platform(), name);
        return gameRoot;
    }

    private void requireOutsideProject(Path outputDirectory) throws IOException {
        if (outputDirectory.toAbsolutePath().startsWith(project.rootDirectory().toAbsolutePath())) {
            throw new IOException("Choose an output directory outside the project.");
        }
    }

    private void injectContent(Path content) throws IOException {
        copyProjectContent(content);
        Files.createDirectories(content.resolve(SCRIPTS_OUTPUT));
    }

    private void copyProjectContent(Path content) throws IOException {
        Path root = project.rootDirectory();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path source : walk.toList()) {
                copyProjectPath(root, source, content);
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

    private static boolean isExcluded(Path relative) {
        String first = relative.getName(0).toString().toLowerCase(Locale.ROOT);
        return EXCLUDED_PROJECT_DIRECTORIES.contains(first);
    }

    private void finalizeLauncher(ExportRequest request, TemplateLayout layout, String name) throws IOException {
        Path targetConfig = layout.config().resolveSibling(name + ".cfg");
        LauncherConfiguration.forGame(request.sceneFileName(), request.title()).writeTo(layout.config(), targetConfig);
        Path targetLauncher = layout.launcher().resolveSibling(name + request.platform().launcherExtension());
        Files.move(layout.launcher(), targetLauncher, StandardCopyOption.REPLACE_EXISTING);
        if (request.platform() == TargetPlatform.LINUX) {
            markExecutable(targetLauncher);
        }
    }

    private static void copyDirectory(Path source, Path destination) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                copyInto(source, path, destination);
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

    private static void archive(Path gameRoot, TargetPlatform platform, String name) throws IOException {
        Path zip = gameRoot.resolveSibling(name + "-" + platform.identifier() + ".zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip));
             Stream<Path> walk = Files.walk(gameRoot)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                writeZipEntry(output, gameRoot.getParent(), file);
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
