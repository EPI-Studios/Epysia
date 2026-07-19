package com.meekdev.box3d;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

// loads the box3d native lib once per jvm, either from the bkun.box3d.library
// system property or by extracting natives/<os>-<arch>/<libname> off the classpath
//
// note: this only satisfies our own System.load call. the jextract bindings in
// box3d_h do their own dlopen by short name on first use (SYMBOL_LOOKUP falls
// back to loaderLookup, but only after libraryLookup by name is tried first and
// that throws if the lib isn't already resolvable by the process' dynamic
// linker). that resolution only looks at LD_LIBRARY_PATH / rpath / ld.so.cache,
// which can't be changed from inside a running jvm, so the native dir has to be
// on LD_LIBRARY_PATH before the jvm starts for box3d_h calls to work
public final class NativeLoader {

    private static boolean loaded = false;
    private static boolean vhacdLoaded = false;

    private NativeLoader() {
    }

    /** true when the optional v-hacd native made it in, decomposeExact needs it */
    public static boolean hasVhacd() {
        load();
        return vhacdLoaded;
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }

        String propertyPath = System.getProperty("bkun.box3d.library");
        if (propertyPath != null && !propertyPath.isBlank()) {
            System.load(Path.of(propertyPath).toAbsolutePath().toString());
            // the vhacd lib is expected right next to a hand picked box3d
            Path sibling = Path.of(propertyPath).toAbsolutePath().getParent()
                    .resolve(System.mapLibraryName("bkunvhacd"));
            if (Files.exists(sibling)) {
                System.load(sibling.toString());
                vhacdLoaded = true;
            }
            loaded = true;
            return;
        }

        Path dir = extractDir();
        loadFrom(dir, "box3d", true);
        vhacdLoaded = loadFrom(dir, "bkunvhacd", false);
        loaded = true;
    }

    private static Path extractDir() {
        try {
            Path tempDir = Files.createTempDirectory("box3d-native");
            tempDir.toFile().deleteOnExit();
            return tempDir;
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("failed to create native extract dir: " + e.getMessage());
        }
    }

    private static boolean loadFrom(Path dir, String name, boolean required) {
        String resourcePath = "natives/" + osArch() + "/" + System.mapLibraryName(name);
        try (InputStream in = openResource(resourcePath)) {
            if (in == null) {
                if (required) {
                    throw new UnsatisfiedLinkError(
                            "could not find " + name + " native library on classpath at " + resourcePath);
                }
                return false;
            }
            Path lib = dir.resolve(System.mapLibraryName(name));
            Files.copy(in, lib, StandardCopyOption.REPLACE_EXISTING);
            lib.toFile().deleteOnExit();
            System.load(lib.toAbsolutePath().toString());
            return true;
        } catch (IOException e) {
            if (required) {
                throw new UnsatisfiedLinkError("failed to extract " + name + " from classpath at "
                        + resourcePath + ": " + e.getMessage());
            }
            return false;
        }
    }

    private static InputStream openResource(String resourcePath) {
        ClassLoader cl = NativeLoader.class.getClassLoader();
        URL url = cl != null ? cl.getResource(resourcePath) : ClassLoader.getSystemResource(resourcePath);
        if (url == null) {
            return null;
        }
        try {
            return url.openStream();
        } catch (IOException e) {
            return null;
        }
    }

    private static String osArch() {
        String osName = System.getProperty("os.name").toLowerCase();
        String archName = System.getProperty("os.arch").toLowerCase();

        String os;
        if (osName.contains("win")) {
            os = "windows";
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            os = "macos";
        } else {
            os = "linux";
        }

        String arch;
        if (archName.contains("aarch64") || archName.contains("arm64")) {
            arch = "arm64";
        } else {
            arch = "x64";
        }

        return os + "-" + arch;
    }
}
