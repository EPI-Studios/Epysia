package fr.epistudio.epysia.physics.rapier;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class RapierNativeLibrary {

    private static volatile boolean loaded;

    private RapierNativeLibrary() {
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        String platformDirectory = resolvePlatformDirectory();
        String libraryFileName = resolveLibraryFileName();
        String resourcePath = "/natives/" + platformDirectory + "/" + libraryFileName;
        extractAndLoad(resourcePath);
        loaded = true;
    }

    private static void extractAndLoad(String resourcePath) {
        try (InputStream stream = RapierNativeLibrary.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new EpysiaException("Rapier native library not bundled at " + resourcePath);
            }
            Path extracted = Files.createTempFile("epysia-rapier-", resolveLibrarySuffix());
            extracted.toFile().deleteOnExit();
            Files.copy(stream, extracted, StandardCopyOption.REPLACE_EXISTING);
            System.load(extracted.toAbsolutePath().toString());
        } catch (IOException exception) {
            throw new EpysiaException("Failed to extract Rapier native library: " + resourcePath);
        }
    }

    private static String resolvePlatformDirectory() {
        String operatingSystem = systemProperty("os.name");
        String architecture = systemProperty("os.arch");
        boolean arm64 = architecture.contains("aarch64") || architecture.contains("arm64");
        if (operatingSystem.contains("linux")) {
            return arm64 ? "linux-arm64" : "linux-x64";
        }
        if (operatingSystem.contains("mac")) {
            return arm64 ? "macos-arm64" : "macos-x64";
        }
        if (operatingSystem.contains("windows")) {
            return "windows-x64";
        }
        throw new EpysiaException("Unsupported operating system for physics natives: " + operatingSystem + " / " + architecture);
    }

    private static String resolveLibraryFileName() {
        String operatingSystem = systemProperty("os.name");
        if (operatingSystem.contains("windows")) {
            return "rapier_moud.dll";
        }
        if (operatingSystem.contains("mac")) {
            return "librapier_moud.dylib";
        }
        return "librapier_moud.so";
    }

    private static String resolveLibrarySuffix() {
        String operatingSystem = systemProperty("os.name");
        if (operatingSystem.contains("windows")) {
            return ".dll";
        }
        if (operatingSystem.contains("mac")) {
            return ".dylib";
        }
        return ".so";
    }

    private static String systemProperty(String key) {
        String value = System.getProperty(key, "");
        return value.toLowerCase();
    }
}
