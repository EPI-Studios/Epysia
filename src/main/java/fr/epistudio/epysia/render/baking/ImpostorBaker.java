package fr.epistudio.epysia.render.baking;

import java.nio.file.Path;
import java.util.List;

public interface ImpostorBaker {

    List<Path> bake(ImpostorBakeRequest request);
}
