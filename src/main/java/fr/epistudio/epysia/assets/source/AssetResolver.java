package fr.epistudio.epysia.assets.source;

import java.util.Optional;

public interface AssetResolver {

    Optional<AssetSource> resolve(String name);

    AssetResolver relativeTo(String subDirectory);
}
