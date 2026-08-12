package fr.epistudio.epysia.editor.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class ExportValidation {

    public record Report(boolean ran, boolean survived, String detail) {

        public static Report skipped(String reason) {
            return new Report(false, true, reason);
        }
    }

    private static final String WINE = "wine";
    private static final float SMOKE_SECONDS = 4.0f;
    private static final long TIMEOUT_SECONDS = 180L;
    private static final int TAIL_LINES = 12;

    private ExportValidation() {
    }

    public static Report run(Path gameRoot, TargetPlatform platform, String launcherName) {
        Optional<Path> launcher = locateLauncher(gameRoot, platform, launcherName);
        if (launcher.isEmpty()) {
            return Report.skipped("launcher not found under " + gameRoot);
        }
        Optional<List<String>> command = commandFor(launcher.get(), platform);
        if (command.isEmpty()) {
            return Report.skipped("wine is not installed, the Windows build was not started");
        }
        return execute(command.get(), gameRoot);
    }

    private static Optional<Path> locateLauncher(Path gameRoot, TargetPlatform platform, String launcherName) {
        Path launcher = platform.layout(gameRoot, launcherName).launcher();
        if (Files.isRegularFile(launcher)) {
            return Optional.of(launcher);
        }
        return Optional.empty();
    }

    private static Optional<List<String>> commandFor(Path launcher, TargetPlatform platform) {
        List<String> command = new ArrayList<>();
        if (platform != GameExporter.hostPlatform()) {
            if (platform != TargetPlatform.WINDOWS || !wineAvailable()) {
                return Optional.empty();
            }
            command.add(WINE);
        }
        command.add(launcher.toAbsolutePath().toString());
        command.add("--validate");
        command.add(Float.toString(SMOKE_SECONDS));
        return Optional.of(command);
    }

    private static boolean wineAvailable() {
        try {
            Process probe = new ProcessBuilder(WINE, "--version")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return probe.waitFor(20L, TimeUnit.SECONDS) && probe.exitValue() == 0;
        } catch (IOException | InterruptedException unavailable) {
            return false;
        }
    }

    private static Report execute(List<String> command, Path workingDirectory) {
        try {
            Path log = Files.createTempFile("epysia-export-validate", ".log");
            Process process = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Report(true, false, "the exported game never exited within "
                        + TIMEOUT_SECONDS + " s\n" + tailOf(log));
            }
            return reportFrom(process.exitValue(), log);
        } catch (IOException | InterruptedException error) {
            return new Report(true, false, "could not start the exported game: " + error);
        }
    }

    private static Report reportFrom(int exitCode, Path log) {
        if (exitCode == 0) {
            return new Report(true, true, "the exported game started and ran headless without crashing");
        }
        return new Report(true, false, "the exported game exited with code " + exitCode + "\n" + tailOf(log));
    }

    private static String tailOf(Path log) {
        try {
            List<String> lines = Files.readAllLines(log);
            return String.join("\n", lines.subList(Math.max(0, lines.size() - TAIL_LINES), lines.size()));
        } catch (IOException unreadable) {
            return "(no log available)";
        }
    }
}
