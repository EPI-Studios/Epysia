package fr.epistudio.epysia.diagnostics;

import fr.epistudio.epysia.logging.Logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class CrashReporter {

    public static final String DIRECTORY_NAME = "crash-reports";

    private static final String NATIVE_PREFIX = "hs_err_pid";

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path directory;
    private final Logger logger;

    public CrashReporter(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
    }

    public static void install(Path workingDirectory, Logger logger) {
        CrashReporter reporter = new CrashReporter(workingDirectory.resolve(DIRECTORY_NAME), logger);
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) ->
                reporter.report(thread.getName(), failure));
        reporter.collectNativeReports(workingDirectory);
        Path launchDirectory = Path.of(System.getProperty("user.dir", "."));
        if (!launchDirectory.toAbsolutePath().equals(workingDirectory.toAbsolutePath())) {
            reporter.collectNativeReports(launchDirectory);
        }
    }

    public static String errorFileArgument(Path workingDirectory) {
        return "-XX:ErrorFile=" + workingDirectory.resolve(DIRECTORY_NAME)
                .resolve(NATIVE_PREFIX + "%p.log").toAbsolutePath();
    }

    public void collectNativeReports(Path workingDirectory) {
        if (!Files.isDirectory(workingDirectory)) {
            return;
        }
        try (Stream<Path> entries = Files.list(workingDirectory)) {
            List<Path> strays = entries.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(NATIVE_PREFIX))
                    .toList();
            if (!strays.isEmpty()) {
                relocate(strays);
            }
        } catch (IOException unreadable) {
            logger.warn("native crash reports could not be collected: " + unreadable.getMessage());
        }
    }

    private void relocate(List<Path> strays) throws IOException {
        Files.createDirectories(directory);
        for (Path stray : strays) {
            try {
                Files.move(stray, directory.resolve(stray.getFileName().toString()),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException immovable) {
                logger.warn("could not move " + stray.getFileName() + ": " + immovable.getMessage());
            }
        }
        logger.info("collected " + strays.size() + " native crash report(s) into " + directory);
    }

    public void report(String threadName, Throwable failure) {
        logger.error("uncaught exception on " + threadName, failure);
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve("crash-" + LocalDateTime.now().format(STAMP) + ".log");
            Files.writeString(file, describe(threadName, failure));
            logger.error("crash report written to " + file.toAbsolutePath());
        } catch (IOException unwritable) {
            logger.error("crash report could not be written: " + unwritable.getMessage());
        }
    }

    private static String describe(String threadName, Throwable failure) {
        StringWriter text = new StringWriter();
        PrintWriter writer = new PrintWriter(text);
        writer.println("thread: " + threadName);
        writer.println("java: " + System.getProperty("java.version"));
        writer.println("os: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        writer.println("memory: " + Runtime.getRuntime().totalMemory() / (1024L * 1024L) + " MB");
        writer.println();
        failure.printStackTrace(writer);
        writer.flush();
        return text.toString();
    }
}
