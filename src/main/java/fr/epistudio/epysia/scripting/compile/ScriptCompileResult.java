package fr.epistudio.epysia.scripting.compile;

import java.util.ArrayList;
import java.util.List;

public record ScriptCompileResult(boolean ok, List<String> messages) {

    public static ScriptCompileResult succeeded() {
        return new ScriptCompileResult(true, List.of());
    }

    public static ScriptCompileResult failed(String message) {
        return new ScriptCompileResult(false, List.of(message));
    }

    public ScriptCompileResult mergedWith(ScriptCompileResult other) {
        List<String> merged = new ArrayList<>(messages);
        merged.addAll(other.messages());
        return new ScriptCompileResult(ok && other.ok(), List.copyOf(merged));
    }
}
