package fr.epistudio.epysia.render.baking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class LightBakerRegistry {

    private final List<LightBaker> bakers = new ArrayList<>();

    public void register(LightBaker baker) {
        bakers.add(baker);
    }

    public List<LightBaker> bakers() {
        return Collections.unmodifiableList(bakers);
    }

    public Optional<LightBaker> firstProducing(LightBakeOutput output) {
        for (LightBaker baker : bakers) {
            if (baker.output() == output) {
                return Optional.of(baker);
            }
        }
        return Optional.empty();
    }
}
