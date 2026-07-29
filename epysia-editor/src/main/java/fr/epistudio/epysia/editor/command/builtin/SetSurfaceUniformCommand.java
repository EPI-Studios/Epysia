package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.render.shader.ShaderUniformValue;
import fr.epistudio.epysia.render.shader.SurfaceUniformHost;

import java.util.Optional;

public final class SetSurfaceUniformCommand implements EditorCommand {

    private final SurfaceUniformHost host;
    private final String uniformName;
    private final Optional<ShaderUniformValue> beforeValue;
    private final ShaderUniformValue afterValue;

    public SetSurfaceUniformCommand(SurfaceUniformHost host, String uniformName,
                                    Optional<ShaderUniformValue> beforeValue, ShaderUniformValue afterValue) {
        this.host = host;
        this.uniformName = uniformName;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
    }

    @Override
    public void apply(CommandContext context) {
        host.surfaceUniforms().set(uniformName, afterValue);
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new SetSurfaceUniformCommand(host, uniformName, Optional.of(afterValue),
                beforeValue.orElse(afterValue));
    }

    @Override
    public String coalesceKey() {
        return "surfaceUniform:" + System.identityHashCode(host) + "." + uniformName;
    }

    @Override
    public String label() {
        return "Set " + uniformName;
    }
}
