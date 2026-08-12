package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.prefab.PrefabApplier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

final class RestorePrefabFileCommand implements EditorCommand {

    private final GameObject instance;
    private final Path prefabFile;
    private final PrefabApplier applier;
    private final Optional<String> restoredText;
    private final Set<String> restoredOverrides;

    RestorePrefabFileCommand(GameObject instance, Path prefabFile, PrefabApplier applier,
                             Optional<String> restoredText, Set<String> restoredOverrides) {
        this.instance = instance;
        this.prefabFile = prefabFile;
        this.applier = applier;
        this.restoredText = restoredText;
        this.restoredOverrides = restoredOverrides;
    }

    @Override
    public void apply(CommandContext context) {
        writeBack();
        instance.clearOverrides();
        for (String key : restoredOverrides) {
            instance.markOverridden(key);
        }
    }

    private void writeBack() {
        try {
            if (restoredText.isPresent()) {
                Files.writeString(prefabFile, restoredText.get());
            } else {
                Files.deleteIfExists(prefabFile);
            }
        } catch (IOException failure) {
            throw new EpysiaException("Could not restore " + prefabFile + ": " + failure.getMessage());
        }
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new ApplyInstanceToPrefabCommand(instance, prefabFile, applier);
    }

    @Override
    public String label() {
        return "Restore " + prefabFile.getFileName();
    }
}
