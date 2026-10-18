package fr.epistudio.epysia.lang.rust;

import fr.epistudio.epysia.scripting.compile.BehaviourTemplate;
import fr.epistudio.epysia.scripting.compile.ScriptCompileResult;
import fr.epistudio.epysia.scripting.compile.ScriptLanguage;
import fr.epistudio.epysia.scripting.editor.SyntaxDescriptor;
import fr.epistudio.epysia.scripting.editor.SyntaxDescriptorFile;
import fr.epistudio.epysia.scripting.foreign.ForeignScriptRuntime;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class RustScriptLanguage implements ScriptLanguage {

    private static final int ORDER = 40;
    private static final String TEMPLATE_RESOURCE = "templates/Behaviour.rs";
    private static final String SYNTAX_RESOURCE = "syntax/rust.epysyntax";

    private final RustRuntime runtime = new RustRuntime();

    @Override
    public String displayName() {
        return "Rust";
    }

    @Override
    public Set<String> sourceExtensions() {
        return Set.of(RustRuntime.SOURCE_EXTENSION);
    }

    @Override
    public String sourceExtension() {
        return RustRuntime.SOURCE_EXTENSION;
    }

    @Override
    public String behaviourTemplate(String className) {
        return BehaviourTemplate.loadedFrom(RustScriptLanguage.class, TEMPLATE_RESOURCE).rendered(className);
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public Optional<SyntaxDescriptor> syntax() {
        return Optional.of(SyntaxDescriptorFile.read(RustScriptLanguage.class, SYNTAX_RESOURCE));
    }

    @Override
    public Optional<ForeignScriptRuntime> foreignRuntime() {
        return Optional.of(runtime);
    }

    @Override
    public ScriptCompileResult compile(List<Path> sources, Path outputDirectory, String classpath) {
        return ScriptCompileResult.succeeded();
    }
}
