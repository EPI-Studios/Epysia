package fr.epistudio.epysia.reflection;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.exceptions.EpysiaException;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ComponentScanner {

    private ComponentScanner() {
    }

    public static List<DiscoveredComponent> scan() {
        return scan(EpysiaComponent.class.getPackageName().split("\\.")[0] + ".*");
    }

    public static List<DiscoveredComponent> scan(String packageFilter) {
        try (ScanResult scanResult = new ClassGraph()
                .enableAllInfo()
                .acceptPackages(packageFilter)
                .scan()) {
            List<DiscoveredComponent> discovered = new ArrayList<>();
            for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(EpysiaComponent.class.getName())) {
                discovered.add(buildEntry(classInfo));
            }
            Collections.sort(discovered, ComponentScanner::compareEntries);
            return discovered;
        }
    }

    private static DiscoveredComponent buildEntry(ClassInfo classInfo) {
        Class<?> rawClass = classInfo.loadClass();
        if (!IComponent.class.isAssignableFrom(rawClass)) {
            throw new EpysiaException("@EpysiaComponent applied to non-IComponent class: " + rawClass.getName());
        }
        @SuppressWarnings("unchecked")
        Class<? extends IComponent> componentClass = (Class<? extends IComponent>) rawClass;
        EpysiaComponent annotation = componentClass.getAnnotation(EpysiaComponent.class);
        return new DiscoveredComponent(
                componentClass,
                annotation.name(),
                annotation.category(),
                annotation.icon(),
                annotation.description());
    }

    private static int compareEntries(DiscoveredComponent left, DiscoveredComponent right) {
        int byCategory = left.category().compareToIgnoreCase(right.category());
        if (byCategory != 0) {
            return byCategory;
        }
        return left.displayName().compareToIgnoreCase(right.displayName());
    }
}
