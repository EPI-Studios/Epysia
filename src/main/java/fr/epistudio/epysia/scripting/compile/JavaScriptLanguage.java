package fr.epistudio.epysia.scripting.compile;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class JavaScriptLanguage implements ScriptLanguage {

    public static final int ORDER = 50;

    private static final String TEMPLATE_RESOURCE = "templates/Behaviour.java";

    @Override
    public String displayName() {
        return "Java";
    }

    @Override
    public Set<String> sourceExtensions() {
        return Set.of(".java");
    }

    @Override
    public String sourceExtension() {
        return ".java";
    }

    @Override
    public String behaviourTemplate(String className) {
        return BehaviourTemplate.loadedFrom(JavaScriptLanguage.class, TEMPLATE_RESOURCE)
                .rendered(className);
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public ScriptCompileResult compile(List<Path> sources, Path outputDirectory, String classpath) {
        return JavaCompilation.run(sources, outputDirectory, classpath);
    }
}
