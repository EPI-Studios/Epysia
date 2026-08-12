package fr.epistudio.epysia.reflection;

import java.util.List;

public final class UserComponents {

    private static volatile List<DiscoveredComponent> published = List.of();

    private UserComponents() {
    }

    public static void publish(List<DiscoveredComponent> components) {
        published = List.copyOf(components);
    }

    public static List<DiscoveredComponent> current() {
        return published;
    }
}
