package fr.epistudio.epysia.scripting.compile;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.reflection.DiscoveredComponent;
import fr.epistudio.epysia.scripting.Behaviour;
import fr.epistudio.epysia.scripting.ProjectRenderSetup;
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
            return new ScriptLoadResult(false, List.of(), List.of(), compiled.messages(), null);
        }
        URL outputUrl = toUrl(outputDirectory);
        if (outputUrl == null) {
            return new ScriptLoadResult(false, List.of(), List.of(), List.of("Invalid script output path."), null);
        }
        ScriptClassLoader loader = new ScriptClassLoader(new URL[]{outputUrl}, ScriptModule.class.getClassLoader());
        try (ScanResult scan = openScan(loader)) {
            return new ScriptLoadResult(true, discoverComponents(scan), discoverRenderSetups(scan),
                    compiled.messages(), loader);
        }
    }

    public static ScriptLoadResult loadPrecompiled(Path classesDirectory) {
        URL classesUrl = toUrl(classesDirectory);
        if (classesUrl == null) {
            return new ScriptLoadResult(false, List.of(), List.of(), List.of("Invalid precompiled scripts path."), null);
        }
        ScriptClassLoader loader = new ScriptClassLoader(new URL[]{classesUrl}, ScriptModule.class.getClassLoader());
        try (ScanResult scan = openScan(loader)) {
            return new ScriptLoadResult(true, discoverComponents(scan), discoverRenderSetups(scan),
                    List.of(), loader);
        }
    }

    private static ScanResult openScan(ScriptClassLoader loader) {
        return new ClassGraph()
                .overrideClassLoaders(loader)
                .ignoreParentClassLoaders()
                .enableAllInfo()
                .scan();
    }

    private static List<Class<? extends ProjectRenderSetup>> discoverRenderSetups(ScanResult scan) {
        List<Class<? extends ProjectRenderSetup>> discovered = new ArrayList<>();
        for (ClassInfo info : scan.getClassesImplementing(ProjectRenderSetup.class.getName())) {
            if (info.isAbstract() || info.isInterface()) {
                continue;
            }
            discovered.add(info.loadClass().asSubclass(ProjectRenderSetup.class));
        }
        return discovered;
    }

    private static List<DiscoveredComponent> discoverComponents(ScanResult scan) {
        List<DiscoveredComponent> discovered = new ArrayList<>();
        {
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
