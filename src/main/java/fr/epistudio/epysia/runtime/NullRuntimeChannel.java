package fr.epistudio.epysia.runtime;

import java.util.Optional;

public final class NullRuntimeChannel implements RuntimeChannel {

    @Override
    public void send(RuntimeEvent event) {
    }

    @Override
    public Optional<RuntimeCommand> pollCommand() {
        return Optional.empty();
    }

    @Override
    public void close() {
    }
}
