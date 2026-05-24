package com.meekdev.psyhou.dialogue;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DialogueLoader {

    private static final Pattern PAUSE_DIRECTIVE = Pattern.compile("^\\s*\\[pause:(\\d+)]\\s*");
    private static final String FILESYSTEM_RESOURCE_ROOT = "examples/resources";

    private DialogueLoader() {
    }

    public static Dialogue loadFromResource(String relativePath) {
        String name = stripExtension(lastSegment(relativePath));
        String contents = readResource(relativePath);
        List<DialogueLine> lines = parseLines(contents);
        return new Dialogue(name, lines);
    }

    private static List<DialogueLine> parseLines(String contents) {
        List<DialogueLine> parsed = new ArrayList<>();
        for (String rawLine : contents.split("\\R")) {
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (!trimmed.startsWith(">")) {
                continue;
            }
            parsed.add(parseLine(trimmed.substring(1).trim()));
        }
        return List.copyOf(parsed);
    }

    private static DialogueLine parseLine(String body) {
        int pauseBeforeMilliseconds = 0;
        String remaining = body;
        Matcher matcher = PAUSE_DIRECTIVE.matcher(remaining);
        if (matcher.find()) {
            pauseBeforeMilliseconds = Integer.parseInt(matcher.group(1));
            remaining = remaining.substring(matcher.end());
        }
        return new DialogueLine(remaining, pauseBeforeMilliseconds);
    }

    private static String readResource(String relativePath) {
        Path filesystemPath = Path.of(FILESYSTEM_RESOURCE_ROOT).resolve(relativePath);
        if (Files.isRegularFile(filesystemPath)) {
            return readFromFilesystem(filesystemPath);
        }
        return readFromClasspath(relativePath);
    }

    private static String readFromFilesystem(Path absolute) {
        try {
            return Files.readString(absolute, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read dialogue file " + absolute, exception);
        }
    }

    private static String readFromClasspath(String relativePath) {
        try (InputStream stream = DialogueLoader.class.getClassLoader().getResourceAsStream(relativePath)) {
            if (stream == null) {
                throw new EpysiaException("Dialogue resource not found: " + relativePath);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read dialogue resource " + relativePath, exception);
        }
    }

    private static String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex < 0 ? fileName : fileName.substring(0, dotIndex);
    }

    private static String lastSegment(String relativePath) {
        int slashIndex = relativePath.lastIndexOf('/');
        return slashIndex < 0 ? relativePath : relativePath.substring(slashIndex + 1);
    }
}
