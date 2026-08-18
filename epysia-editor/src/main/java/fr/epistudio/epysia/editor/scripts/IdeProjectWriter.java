package fr.epistudio.epysia.editor.scripts;

import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.scripting.compile.ScriptLanguage;
import fr.epistudio.epysia.scripting.compile.ScriptLanguages;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

public final class IdeProjectWriter {

    private static final String BUILD_FILENAME = "build.gradle";
    private static final String SETTINGS_FILENAME = "settings.gradle";
    private static final String PROPERTIES_FILENAME = "gradle.properties";
    private static final String GITIGNORE_FILENAME = ".gitignore";
    private static final String CLASSPATH_PROPERTY = "epysiaEngineClasspath";
    private static final int JAVA_LANGUAGE_VERSION = 25;
    private static final String INDENT = "    ";
    private static final List<String> IGNORED_PATHS = List.of(
            ".epysia/", ".gradle/", "build/", "out/", ".idea/", "*.iml", PROPERTIES_FILENAME);

    private IdeProjectWriter() {
    }

    public static Optional<String> write(Project project) {
        return write(project, ScriptLanguages.discover(project.libraries()));
    }

    public static Optional<String> write(Project project, ScriptLanguages languages) {
        Path root = project.rootDirectory();
        try {
            Files.writeString(root.resolve(SETTINGS_FILENAME), settingsScript(project));
            Files.writeString(root.resolve(BUILD_FILENAME), buildScript(project, languages));
            writeEngineClasspath(root);
            writeGitignore(root);
            return Optional.empty();
        } catch (IOException failure) {
            return Optional.of("Could not write the IDE project files: " + failure.getMessage());
        }
    }

    private static String settingsScript(Project project) {
        return "rootProject.name = '" + quoted(project.name()) + "'\n";
    }

    private static String buildScript(Project project, ScriptLanguages languages) {
        StringBuilder script = new StringBuilder();
        appendPlugins(script, languages);
        appendToolchain(script);
        appendSourceSets(script, languages);
        appendEngineClasspath(script);
        appendDependencies(script);
        return script.toString();
    }

    private static void appendPlugins(StringBuilder script, ScriptLanguages languages) {
        line(script, 0, "plugins {");
        line(script, 1, "id 'java'");
        for (ScriptLanguage language : languages.authoringOrder()) {
            language.gradlePlugins().forEach(plugin -> line(script, 1, plugin));
        }
        line(script, 0, "}");
        script.append('\n');
    }

    private static void appendToolchain(StringBuilder script) {
        line(script, 0, "java {");
        line(script, 1, "toolchain {");
        line(script, 2, "languageVersion = JavaLanguageVersion.of(" + JAVA_LANGUAGE_VERSION + ")");
        line(script, 1, "}");
        line(script, 0, "}");
        script.append('\n');
    }

    private static void appendSourceSets(StringBuilder script, ScriptLanguages languages) {
        line(script, 0, "sourceSets {");
        line(script, 1, "main {");
        appendSourceDirectory(script, "java");
        for (ScriptLanguage language : languages.authoringOrder()) {
            if (!language.sourceDirectoryName().isEmpty()) {
                appendSourceDirectory(script, language.sourceDirectoryName());
            }
        }
        line(script, 2, "resources {");
        line(script, 3, "srcDirs = []");
        line(script, 2, "}");
        line(script, 1, "}");
        line(script, 0, "}");
        script.append('\n');
    }

    private static void appendSourceDirectory(StringBuilder script, String language) {
        line(script, 2, language + " {");
        line(script, 3, "srcDirs = ['" + Project.SCRIPTS_DIRECTORY_NAME + "']");
        line(script, 2, "}");
    }

    private static void appendEngineClasspath(StringBuilder script) {
        line(script, 0, "def engineClasspath = providers.gradleProperty('" + CLASSPATH_PROPERTY + "')");
        line(script, 2, ".getOrElse('').split(File.pathSeparator).findAll { !it.isEmpty() }");
        script.append('\n');
    }

    private static void appendDependencies(StringBuilder script) {
        line(script, 0, "dependencies {");
        line(script, 1, "implementation files(engineClasspath)");
        appendLibraryTree(script, Project.LIBRARIES_DIRECTORY_NAME);
        appendLibraryTree(script, Project.LIBRARIES_CACHE_DIRECTORY_NAME);
        line(script, 0, "}");
    }

    private static void appendLibraryTree(StringBuilder script, String directory) {
        line(script, 1, "implementation fileTree(dir: '" + directory + "', include: '*.jar')");
    }

    private static void line(StringBuilder script, int depth, String text) {
        script.append(INDENT.repeat(depth)).append(text).append('\n');
    }

    private static void writeEngineClasspath(Path root) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(CLASSPATH_PROPERTY, String.join(File.pathSeparator,
                engineClasspath().stream().map(Path::toString).toList()));
        try (Writer writer = Files.newBufferedWriter(root.resolve(PROPERTIES_FILENAME))) {
            properties.store(writer, "Written by the Epysia editor. Machine specific, do not commit.");
        }
    }

    private static void writeGitignore(Path root) throws IOException {
        Path file = root.resolve(GITIGNORE_FILENAME);
        if (Files.exists(file)) {
            return;
        }
        Files.writeString(file, String.join("\n", IGNORED_PATHS) + "\n");
    }

    private static List<Path> engineClasspath() {
        Set<Path> entries = new LinkedHashSet<>();
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (!entry.isBlank()) {
                entries.add(Path.of(entry).toAbsolutePath().normalize());
            }
        }
        return entries.stream().filter(Files::exists).toList();
    }

    private static String quoted(String text) {
        return text.replace("\\", "\\\\").replace("'", "\\'");
    }
}
