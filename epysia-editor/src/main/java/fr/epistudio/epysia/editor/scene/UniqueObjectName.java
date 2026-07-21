package fr.epistudio.epysia.editor.scene;

import fr.epistudio.epysia.scene.Scene;

import java.util.regex.Pattern;

public final class UniqueObjectName {

    private static final Pattern COPY_SUFFIXES = Pattern.compile("(\\s*\\(copy\\))+$");
    private static final Pattern TRAILING_INDEX = Pattern.compile("\\s+\\d+$");
    private static final int FIRST_INDEX = 2;

    private UniqueObjectName() {
    }

    public static String in(Scene scene, String desiredName) {
        String base = baseOf(desiredName);
        if (base.equals(desiredName) && scene.findByName(base).isEmpty()) {
            return base;
        }
        int index = FIRST_INDEX;
        while (scene.findByName(base + " " + index).isPresent()) {
            index++;
        }
        return base + " " + index;
    }

    private static String baseOf(String name) {
        String withoutCopies = COPY_SUFFIXES.matcher(name).replaceAll("").strip();
        String withoutIndex = TRAILING_INDEX.matcher(withoutCopies).replaceAll("").strip();
        return withoutIndex.isEmpty() ? withoutCopies : withoutIndex;
    }
}
