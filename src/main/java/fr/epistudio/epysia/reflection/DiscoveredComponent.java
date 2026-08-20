package fr.epistudio.epysia.reflection;

import fr.epistudio.epysia.components.IComponent;

public record DiscoveredComponent(
        Class<? extends IComponent> componentClass,
        String displayName,
        String category,
        String icon,
        String description
) {
}
