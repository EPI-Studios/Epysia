package fr.epistudio.epysia.scripting;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.scene.Scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AutoloadSystem implements GameSystem {

    private final List<Autoload> running = new ArrayList<>();
    private List<Class<? extends Autoload>> types = List.of();
    private EngineServices services;

    @Override
    public void initialize(EngineServices services) {
        this.services = services;
    }

    public void useTypes(List<Class<? extends Autoload>> autoloadTypes) {
        types = List.copyOf(autoloadTypes);
    }

    public void start() {
        stop();
        for (Class<? extends Autoload> type : types) {
            instantiate(type).ifPresent(this::startOne);
        }
    }

    public void stop() {
        for (Autoload autoload : running.reversed()) {
            try {
                autoload.onStop();
            } catch (RuntimeException error) {
                logError("onStop", autoload.getClass(), error);
            }
        }
        running.clear();
    }

    public <T extends Autoload> Optional<T> find(Class<T> type) {
        for (Autoload autoload : running) {
            if (type.isInstance(autoload)) {
                return Optional.of(type.cast(autoload));
            }
        }
        return Optional.empty();
    }

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        for (Autoload autoload : List.copyOf(running)) {
            try {
                autoload.onUpdate(input, deltaTimeSeconds);
            } catch (RuntimeException error) {
                logError("onUpdate", autoload.getClass(), error);
            }
        }
    }

    @Override
    public void shutdown() {
        stop();
    }

    private Optional<Autoload> instantiate(Class<? extends Autoload> type) {
        try {
            return Optional.of(type.getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException | RuntimeException error) {
            logError("constructor", type, error);
            return Optional.empty();
        }
    }

    private void startOne(Autoload autoload) {
        running.add(autoload);
        try {
            autoload.onStart(services);
            services.logger().info("[scripts] started autoload " + autoload.getClass().getName());
        } catch (RuntimeException error) {
            logError("onStart", autoload.getClass(), error);
        }
    }

    private void logError(String hook, Class<?> type, Exception error) {
        services.logger().error("[Autoload] " + hook + " threw in " + type.getName(), error);
    }
}
