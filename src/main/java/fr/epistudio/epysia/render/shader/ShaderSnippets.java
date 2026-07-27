package fr.epistudio.epysia.render.shader;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ShaderSnippets {

    private static final Map<String, String> SOURCES = new ConcurrentHashMap<>();

    private ShaderSnippets() {
    }

    public static String line(String path) {
        return SOURCES.computeIfAbsent(path, ShaderSnippets::read);
    }

    public static String block(String path) {
        return line(path) + "\n";
    }

    private static String read(String path) {
        return ShaderLoader.autoDetect().load(path).source().stripTrailing();
    }
}
