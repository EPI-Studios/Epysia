package fr.epistudio.epysia.lang.kotlin;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

final class KotlinRuntimeArchives {

    private static final String RUNTIME_PREFIX = "epysia-lang-kotlin-runtime-";
    private static final String ARCHIVE_SUFFIX = ".jar";

    private KotlinRuntimeArchives() {
    }

    static List<Path> located() {
        return packDirectory().map(KotlinRuntimeArchives::runtimeArchivesIn).orElse(List.of());
    }

    private static Optional<Path> packDirectory() {
        CodeSource source = KotlinScriptLanguage.class.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(Path.of(source.getLocation().toURI()).getParent());
        } catch (URISyntaxException unreadable) {
            return Optional.empty();
        }
    }

    private static List<Path> runtimeArchivesIn(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.filter(KotlinRuntimeArchives::isRuntimeArchive).toList();
        } catch (IOException unreadable) {
            return List.of();
        }
    }

    private static boolean isRuntimeArchive(Path candidate) {
        String name = candidate.getFileName().toString();
        return name.startsWith(RUNTIME_PREFIX) && name.endsWith(ARCHIVE_SUFFIX);
    }
}
