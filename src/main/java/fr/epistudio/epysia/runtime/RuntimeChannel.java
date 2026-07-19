package fr.epistudio.epysia.runtime;

import java.util.Optional;

public interface RuntimeChannel {

    void send(RuntimeEvent event);

    Optional<RuntimeCommand> pollCommand();

    void close();
}
