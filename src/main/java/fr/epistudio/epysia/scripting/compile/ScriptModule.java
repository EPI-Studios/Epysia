package fr.epistudio.epysia.scripting.compile;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.project.ProjectLibraries;
import fr.epistudio.epysia.reflection.DiscoveredComponent;
import fr.epistudio.epysia.scripting.ProjectRenderSetup;
import fr.epistudio.epysia.scripting.foreign.ForeignClassEmitter;
import fr.epistudio.epysia.scripting.foreign.ForeignClassLoader;
import fr.epistudio.epysia.scripting.foreign.ForeignComponentBootstrap;
import fr.epistudio.epysia.scripting.foreign.ForeignComponentType;
import fr.epistudio.epysia.scripting.foreign.ForeignScriptRuntime;
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

    public static ScriptLoadResult load(Path scriptsDirectory, Path outputDirectory, ProjectLibraries libraries) {
        ScriptLanguages languages = ScriptLanguages.discover(libraries);
        ScriptCompileResult compiled = languages.compileAll(scriptsDirectory, outputDirectory, libraries);
        if (!compiled.ok()) {
            return new ScriptLoadResult(false, List.of(), List.of(), compiled.messages(), null);
        }
        ScriptLoadResult scanned = scan(outputDirectory, libraries, compiled.messages());
        if (!scanned.ok()) {
            return scanned;
        }
        return withForeignComponents(scanned, languages, scriptsDirectory);
    }

    private static ScriptLoadResult withForeignComponents(ScriptLoadResult scanned,
                                                          ScriptLanguages languages,
                                                          Path scriptsDirectory) {
        List<ForeignScriptRuntime> runtimes = new ArrayList<>();
        languages.languages().forEach(language -> language.foreignRuntime().ifPresent(runtimes::add));
        if (runtimes.isEmpty()) {
            return scanned;
        }
        List<String> messages = new ArrayList<>(scanned.messages());
        List<DiscoveredComponent> components = new ArrayList<>(scanned.components());
        ForeignClassEmitter emitter = new ForeignClassEmitter(new ForeignClassLoader(scanned.loader()));
        ForeignComponentBootstrap.clear();
        for (ForeignScriptRuntime runtime : runtimes) {
            for (ForeignComponentType type : runtime.load(scriptsDirectory, messages::add)) {
                components.add(foreignComponentOf(emitter, runtime, type));
            }
        }
        return new ScriptLoadResult(true, List.copyOf(components), scanned.renderSetups(),
                List.copyOf(messages), scanned.loader());
    }

    private static DiscoveredComponent foreignComponentOf(ForeignClassEmitter emitter,
                                                          ForeignScriptRuntime runtime,
                                                          ForeignComponentType type) {
        String key = runtime.displayName() + "_" + type.name();
        return new DiscoveredComponent(emitter.define(key, type), type.name(), type.category(),
                "SCRIPT", type.description());
    }

    public static ScriptLoadResult loadPrecompiled(Path classesDirectory, Path scriptsDirectory,
                                                   ProjectLibraries libraries) {
        ScriptLoadResult scanned = scan(classesDirectory, libraries, List.of());
        if (!scanned.ok()) {
            return scanned;
        }
        return withForeignComponents(scanned, ScriptLanguages.discover(libraries), scriptsDirectory);
    }

    private static ScriptLoadResult scan(Path classesDirectory, ProjectLibraries libraries, List<String> messages) {
        URL classesUrl = toUrl(classesDirectory);
        if (classesUrl == null) {
            return new ScriptLoadResult(false, List.of(), List.of(), List.of("Invalid script output path."), null);
        }
        List<URL> urls = new ArrayList<>();
        urls.add(classesUrl);
        urls.addAll(libraries.urls());
        ScriptClassLoader loader = new ScriptClassLoader(urls.toArray(URL[]::new),
                ScriptModule.class.getClassLoader());
        try (ScanResult scanned = openScan(loader)) {
            return new ScriptLoadResult(true, discoverComponents(scanned), discoverRenderSetups(scanned),
                    messages, loader);
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
                if (!IComponent.class.isAssignableFrom(raw) || info.isAbstract() || info.isInterface()) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Class<? extends IComponent> componentClass = (Class<? extends IComponent>) raw;
                EpysiaComponent annotation = componentClass.getAnnotation(EpysiaComponent.class);
                discovered.add(new DiscoveredComponent(componentClass,
                        annotation.name(), annotation.category(), annotation.icon(),
                        annotation.description()));
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
