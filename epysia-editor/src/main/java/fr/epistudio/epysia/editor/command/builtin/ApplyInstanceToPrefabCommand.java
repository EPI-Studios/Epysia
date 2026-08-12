package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.prefab.PrefabApplier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public final class ApplyInstanceToPrefabCommand implements EditorCommand {

    private final GameObject instance;
    private final Path prefabFile;
    private final PrefabApplier applier;
    private final Optional<String> replacedText;
    private final Set<String> clearedOverrides;

    public ApplyInstanceToPrefabCommand(GameObject instance, Path prefabFile, PrefabApplier applier) {
        this(instance, prefabFile, applier, readIfPresent(prefabFile),
                new LinkedHashSet<>(instance.overriddenProperties()));
    }

    private ApplyInstanceToPrefabCommand(GameObject instance, Path prefabFile, PrefabApplier applier,
                                         Optional<String> replacedText, Set<String> clearedOverrides) {
        this.instance = instance;
        this.prefabFile = prefabFile;
        this.applier = applier;
        this.replacedText = replacedText;
        this.clearedOverrides = clearedOverrides;
    }

    @Override
    public void apply(CommandContext context) {
        try {
            applier.applyToPrefab(instance, prefabFile);
        } catch (IOException failure) {
            throw new EpysiaException("Could not write " + prefabFile + ": " + failure.getMessage());
        }
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new RestorePrefabFileCommand(instance, prefabFile, applier,
                replacedText, clearedOverrides);
    }

    @Override
    public String label() {
        return "Apply " + instance.name() + " to " + prefabFile.getFileName();
    }

    static Optional<String> readIfPresent(Path file) {
        try {
            return Files.isRegularFile(file) ? Optional.of(Files.readString(file)) : Optional.empty();
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }
}
