package fr.epistudio.epysia.logging;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class LogFile {

    private LogFile() {
    }

    public static PrintStream open(Path path) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            return new PrintStream(Files.newOutputStream(path,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING), true, StandardCharsets.UTF_8);
        } catch (IOException error) {
            System.err.println("[LogFile] could not open " + path + ": " + error.getMessage());
            return System.err;
        }
    }
}
