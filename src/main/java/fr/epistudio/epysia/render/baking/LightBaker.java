package fr.epistudio.epysia.render.baking;

import java.nio.file.Path;
import java.util.Optional;

public interface LightBaker {

    LightBakeOutput output();

    void start(BakeRequest request);

    BakeProgress step();

    void cancel();

    Optional<Path> result();
}
