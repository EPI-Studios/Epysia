package fr.epistudio.epysia.gpu;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class GpuLauncher {

    private static final String GUARD_VARIABLE = "EPYSIA_GPU_ENFORCED";
    private static final String REGISTRY_KEY = "HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences";

    private GpuLauncher() {
    }

    public static void enforce(GpuPreference preference) {
        if (preference == GpuPreference.SYSTEM_DEFAULT || System.getenv(GUARD_VARIABLE) != null) {
            return;
        }
        if (isWindows()) {
            writeWindowsPreference(preference);
        } else {
            relaunchWithGpuEnvironment(preference);
        }
    }

    public static void persist(GpuPreference preference) {
        if (isWindows()) {
            writeWindowsPreference(preference);
        }
    }

    public static void applyEnvironment(Map<String, String> environment, GpuPreference preference) {
        if (preference == GpuPreference.HIGH_PERFORMANCE) {
            environment.put("DRI_PRIME", "1");
            environment.put("__NV_PRIME_RENDER_OFFLOAD", "1");
            environment.put("__GLX_VENDOR_LIBRARY_NAME", "nvidia");
            environment.put("__VK_LAYER_NV_optimus", "NVIDIA_only");
        } else if (preference == GpuPreference.POWER_SAVING) {
            environment.put("DRI_PRIME", "0");
        }
    }

    private static void relaunchWithGpuEnvironment(GpuPreference preference) {
        Optional<List<String>> commandLine = currentCommandLine();
        if (commandLine.isEmpty()) {
            return;
        }
        ProcessBuilder builder = new ProcessBuilder(commandLine.get()).inheritIO();
        applyEnvironment(builder.environment(), preference);
        builder.environment().put(GUARD_VARIABLE, "1");
        try {
            Process child = builder.start();
            System.exit(child.waitFor());
        } catch (IOException ignored) {
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static Optional<List<String>> currentCommandLine() {
        ProcessHandle.Info info = ProcessHandle.current().info();
        String[] arguments = info.arguments().orElse(new String[0]);
        if (info.command().isEmpty() || arguments.length == 0) {
            return rebuiltCommandLine();
        }
        List<String> argv = new ArrayList<>();
        argv.add(info.command().get());
        argv.addAll(List.of(arguments));
        return Optional.of(argv);
    }

    private static Optional<List<String>> rebuiltCommandLine() {
        String entryPoint = System.getProperty("sun.java.command", "");
        String classPath = System.getProperty("java.class.path", "");
        if (entryPoint.isBlank() || classPath.isBlank()) {
            return Optional.empty();
        }
        List<String> argv = new ArrayList<>();
        argv.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        argv.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
        argv.add("-cp");
        argv.add(classPath);
        argv.addAll(List.of(entryPoint.split(" ")));
        return Optional.of(argv);
    }

    private static void writeWindowsPreference(GpuPreference preference) {
        Optional<String> executable = ProcessHandle.current().info().command();
        if (executable.isEmpty() || executable.get().toLowerCase().endsWith("java.exe")) {
            return;
        }
        int value = switch (preference) {
            case SYSTEM_DEFAULT -> 0;
            case POWER_SAVING -> 1;
            case HIGH_PERFORMANCE -> 2;
        };
        List<String> command = List.of("reg", "add", REGISTRY_KEY, "/v", executable.get(),
                "/t", "REG_SZ", "/d", "GpuPreference=" + value + ";", "/f");
        try {
            new ProcessBuilder(command).start();
        } catch (IOException ignored) {
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
