package fr.epistudio.epysia.editor.scripts;

import fr.epistudio.epysia.project.Project;
import kotlin.KotlinVersion;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class IdeProjectWriter {

    private static final String BUILD_FILENAME = "build.gradle";
    private static final String SETTINGS_FILENAME = "settings.gradle";
    private static final int JAVA_LANGUAGE_VERSION = 25;
    private static final String INDENT = "    ";

    private IdeProjectWriter() {
    }

    public static Optional<String> write(Project project) {
        Path root = project.rootDirectory();
        try {
            Files.writeString(root.resolve(SETTINGS_FILENAME), settingsScript(project));
            Files.writeString(root.resolve(BUILD_FILENAME), buildScript(project));
            return Optional.empty();
        } catch (IOException failure) {
            return Optional.of("Could not write the IDE project files: " + failure.getMessage());
        }
    }

    private static String settingsScript(Project project) {
        return "rootProject.name = '" + quoted(project.name()) + "'\n";
    }

    private static String buildScript(Project project) {
        boolean usesKotlin = KotlinRuntimeInstaller.hasKotlinSources(project.scriptsDirectory());
        StringBuilder script = new StringBuilder();
        appendPlugins(script, usesKotlin);
        appendToolchain(script);
        appendSourceSets(script, usesKotlin);
        appendDependencies(script);
        return script.toString();
    }

    private static void appendPlugins(StringBuilder script, boolean usesKotlin) {
        line(script, 0, "plugins {");
        line(script, 1, "id 'java'");
        if (usesKotlin) {
            line(script, 1, "id 'org.jetbrains.kotlin.jvm' version '" + KotlinVersion.CURRENT + "'");
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

    private static void appendSourceSets(StringBuilder script, boolean usesKotlin) {
        line(script, 0, "sourceSets {");
        line(script, 1, "main {");
        appendSourceDirectory(script, "java");
        if (usesKotlin) {
            appendSourceDirectory(script, "kotlin");
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

    private static void appendDependencies(StringBuilder script) {
        line(script, 0, "dependencies {");
        line(script, 1, "implementation files(");
        appendEngineClasspath(script);
        line(script, 1, ")");
        appendLibraryTree(script, Project.LIBRARIES_DIRECTORY_NAME);
        appendLibraryTree(script, Project.LIBRARIES_CACHE_DIRECTORY_NAME);
        line(script, 0, "}");
    }

    private static void appendEngineClasspath(StringBuilder script) {
        List<Path> entries = engineClasspath();
        for (int index = 0; index < entries.size(); index++) {
            String separator = index < entries.size() - 1 ? "," : "";
            line(script, 2, "'" + quoted(entries.get(index).toString()) + "'" + separator);
        }
    }

    private static void appendLibraryTree(StringBuilder script, String directory) {
        line(script, 1, "implementation fileTree(dir: '" + directory + "', include: '*.jar')");
    }

    private static void line(StringBuilder script, int depth, String text) {
        script.append(INDENT.repeat(depth)).append(text).append('\n');
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
