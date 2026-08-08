package fr.epistudio.epysia.editor.scripts;

import fr.epistudio.epysia.scripting.compile.ScriptCompileResult;
import fr.epistudio.epysia.scripting.compile.ScriptLanguage;
import org.jetbrains.kotlin.cli.common.ExitCode;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation;
import org.jetbrains.kotlin.cli.common.messages.MessageCollector;
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;
import org.jetbrains.kotlin.config.Services;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.scripting.Behaviour;

public final class KotlinScriptLanguage implements ScriptLanguage {

    public static final int ORDER = 10;
    private static final String JVM_TARGET = "21";

    private static final String TEMPLATE = """
            import fr.epistudio.epysia.EngineServices
            import fr.epistudio.epysia.components.EpysiaComponent
            import fr.epistudio.epysia.components.Export
            import fr.epistudio.epysia.input.InputState
            import fr.epistudio.epysia.scripting.Behaviour

            @EpysiaComponent(name = "%s", category = "Scripts")
            class %s : Behaviour() {

                @field:Export(label = "Speed")
                private var speed = 1.0f

                override fun onStart(services: EngineServices) {
                }

                override fun onUpdate(input: InputState, deltaTimeSeconds: Float) {
                }
            }
            """;

    @Override
    public String displayName() {
        return "Kotlin";
    }

    @Override
    public Set<String> sourceExtensions() {
        return Set.of(".kt");
    }

    @Override
    public String sourceExtension() {
        return ".kt";
    }

    @Override
    public String behaviourTemplate(String className) {
        return TEMPLATE.formatted(className, className);
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public ScriptCompileResult compile(List<Path> sources, Path outputDirectory, String classpath) {
        CollectingMessageCollector collector = new CollectingMessageCollector();
        ExitCode code = new K2JVMCompiler().exec(collector, Services.EMPTY,
                argumentsFor(sources, outputDirectory, classpath));
        return new ScriptCompileResult(code == ExitCode.OK, collector.messages());
    }

    private static K2JVMCompilerArguments argumentsFor(List<Path> sources, Path outputDirectory,
                                                       String classpath) {
        K2JVMCompilerArguments arguments = new K2JVMCompilerArguments();
        arguments.setFreeArgs(sources.stream().map(path -> path.toAbsolutePath().toString()).toList());
        arguments.setDestination(outputDirectory.toAbsolutePath().toString());
        arguments.setClasspath(classpath);
        arguments.setJvmTarget(JVM_TARGET);
        arguments.setNoStdlib(true);
        arguments.setNoReflect(true);
        return arguments;
    }

    private static final class CollectingMessageCollector implements MessageCollector {

        private final List<String> collected = new ArrayList<>();
        private boolean sawError;

        @Override
        public void clear() {
            collected.clear();
        }

        @Override
        public boolean hasErrors() {
            return sawError;
        }

        @Override
        public void report(CompilerMessageSeverity severity, String message,
                           CompilerMessageSourceLocation location) {
            if (severity.isError()) {
                sawError = true;
            }
            if (severity == CompilerMessageSeverity.LOGGING) {
                return;
            }
            collected.add(format(severity, message, location));
        }

        private static String format(CompilerMessageSeverity severity, String message,
                                     CompilerMessageSourceLocation location) {
            if (location == null) {
                return severity.getPresentableName() + " kotlin: " + message;
            }
            return severity.getPresentableName() + " " + location.getPath() + ":" + location.getLine()
                    + ": " + message;
        }

        private List<String> messages() {
            return List.copyOf(collected);
        }
    }
}
