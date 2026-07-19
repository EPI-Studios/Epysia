package fr.epistudio.epysia.assets.source;

import java.io.InputStream;
import java.util.Optional;

public interface AssetSource {

    String path();

    Optional<InputStream> open();
}
