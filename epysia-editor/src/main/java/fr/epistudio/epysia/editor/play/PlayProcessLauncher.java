package fr.epistudio.epysia.editor.play;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PlayProcessLauncher {

    private static final String GAME_LAUNCHER_CLASS = "fr.epistudio.epysia.GameLauncher";
    private static final String STDIO_FLAG = "--runtime-channel=stdio";
    private static final String NATIVE_ACCESS_JVM_FLAG = "--enable-native-access=ALL-UNNAMED";

    private PlayProcessLauncher() {
    }

    public static Process launch(Path scenePath, Path projectRoot, String windowTitle) throws IOException {
        return start(buildCommand(scenePath, projectRoot, windowTitle));
    }

    public static Process launchClient(Path scenePath, Path projectRoot, String windowTitle,
                                       String host, int port) throws IOException {
        List<String> command = buildCommand(scenePath, projectRoot, windowTitle);
        command.add("--connect");
        command.add(host);
        command.add("--port");
        command.add(Integer.toString(port));
        return start(command);
    }

    public static Process launchListenServer(Path scenePath, Path projectRoot, String windowTitle,
                                             int port) throws IOException {
        List<String> command = buildCommand(scenePath, projectRoot, windowTitle);
        command.add("--listen-server");
        command.add("--port");
        command.add(Integer.toString(port));
        return start(command);
    }

    public static Process launchDedicatedServer(Path scenePath, Path projectRoot, int port) throws IOException {
        List<String> command = buildCommand(scenePath, projectRoot, "Epysia - Server");
        command.add("--server");
        command.add("--port");
        command.add(Integer.toString(port));
        return start(command);
    }

    private static Process start(List<String> command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(false);
        return builder.start();
    }

    private static List<String> buildCommand(Path scenePath, Path projectRoot, String windowTitle) {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add(NATIVE_ACCESS_JVM_FLAG);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(GAME_LAUNCHER_CLASS);
        command.add("--scene");
        command.add(scenePath.toString());
        if (projectRoot != null) {
            command.add("--project");
            command.add(projectRoot.toString());
        }
        command.add("--title");
        command.add(windowTitle);
        command.add(STDIO_FLAG);
        return command;
    }

    private static String javaExecutable() {
        String javaHome = System.getProperty("java.home");
        String binary = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        return Path.of(javaHome, "bin", binary).toString();
    }
}
