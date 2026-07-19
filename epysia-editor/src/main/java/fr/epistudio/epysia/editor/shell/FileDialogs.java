package fr.epistudio.epysia.editor.shell;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Optional;

public final class FileDialogs {

    private FileDialogs() {
    }

    public static Optional<Path> pickFolder(String title, Path initialDirectory) {
        String result = TinyFileDialogs.tinyfd_selectFolderDialog(title, initialDirectory.toString());
        return result == null ? Optional.empty() : Optional.of(Path.of(result));
    }

    public static Optional<Path> pickFile(String title, Path initialDirectory, String filterPattern,
                                          String filterDescription) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var patterns = stack.mallocPointer(1);
            ByteBuffer pattern = stack.UTF8(filterPattern);
            patterns.put(pattern).flip();
            String result = TinyFileDialogs.tinyfd_openFileDialog(title,
                    initialDirectory.toString() + "/", patterns, filterDescription, false);
            return result == null ? Optional.empty() : Optional.of(Path.of(result));
        }
    }
}
