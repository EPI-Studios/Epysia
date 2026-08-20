package fr.epistudio.epysia.pool;

import fr.epistudio.epysia.EngineServices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

final class PrefabText {

    private PrefabText() {
    }

    static Optional<String> read(EngineServices services, String prefabPath) {
        return resolve(services, prefabPath).flatMap(PrefabText::readFile);
    }

    private static Optional<Path> resolve(EngineServices services, String prefabPath) {
        return services.assets().locator().file(prefabPath);
    }

    private static Optional<String> readFile(Path path) {
        try {
            return Optional.of(Files.readString(path));
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }
}
