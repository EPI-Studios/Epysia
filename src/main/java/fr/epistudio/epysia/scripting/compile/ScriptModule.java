package fr.epistudio.epysia.scripting.compile;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.reflection.DiscoveredComponent;
import fr.epistudio.epysia.scripting.Behaviour;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ScriptModule {

    private ScriptModule() {
    }

    public static ScriptLoadResult load(Path scriptsDirectory, Path outputDirectory) {
        ScriptCompiler.Result compiled = new ScriptCompiler().compile(scriptsDirectory, outputDirectory);
        if (!compiled.ok()) {
            return new ScriptLoadResult(false, List.of(), compiled.messages(), null);
        }
        URL outputUrl = toUrl(outputDirectory);
        if (outputUrl == null) {
            return new ScriptLoadResult(false, List.of(), List.of("Invalid script output path."), null);
        }
        ScriptClassLoader loader = new ScriptClassLoader(new URL[]{outputUrl}, ScriptModule.class.getClassLoader());
        List<DiscoveredComponent> components = discover(loader);
        return new ScriptLoadResult(true, components, compiled.messages(), loader);
    }

    public static ScriptLoadResult loadPrecompiled(Path classesDirectory) {
        URL classesUrl = toUrl(classesDirectory);
        if (classesUrl == null) {
            return new ScriptLoadResult(false, List.of(), List.of("Invalid precompiled scripts path."), null);
        }
        ScriptClassLoader loader = new ScriptClassLoader(new URL[]{classesUrl}, ScriptModule.class.getClassLoader());
        List<DiscoveredComponent> components = discover(loader);
        return new ScriptLoadResult(true, components, List.of(), loader);
    }

    private static List<DiscoveredComponent> discover(ScriptClassLoader loader) {
        List<DiscoveredComponent> discovered = new ArrayList<>();
        try (ScanResult scan = new ClassGraph()
                .overrideClassLoaders(loader)
                .ignoreParentClassLoaders()
                .enableAllInfo()
                .scan()) {
            for (ClassInfo info : scan.getClassesWithAnnotation(EpysiaComponent.class.getName())) {
                Class<?> raw = info.loadClass();
                if (!Behaviour.class.isAssignableFrom(raw) || !IComponent.class.isAssignableFrom(raw)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Class<? extends IComponent> componentClass = (Class<? extends IComponent>) raw;
                EpysiaComponent annotation = componentClass.getAnnotation(EpysiaComponent.class);
                discovered.add(new DiscoveredComponent(componentClass,
                        annotation.name(), annotation.category(), annotation.icon()));
            }
        }
        return discovered;
    }

    private static URL toUrl(Path directory) {
        try {
            return directory.toUri().toURL();
        } catch (MalformedURLException exception) {
            return null;
        }
    }
}
