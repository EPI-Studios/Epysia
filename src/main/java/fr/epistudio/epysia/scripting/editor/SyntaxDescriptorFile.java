package fr.epistudio.epysia.scripting.editor;

import fr.epistudio.epysia.scene.serialization.JsonReader;
import fr.epistudio.epysia.scripting.LanguageResource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SyntaxDescriptorFile {

    private SyntaxDescriptorFile() {
    }

    public static SyntaxDescriptor read(Class<?> owner, String resourceName) {
        Map<String, Object> document =
                new JsonReader(LanguageResource.read(owner, resourceName)).readRootObject();
        return new SyntaxDescriptor(
                text(document, "displayName", owner.getSimpleName()),
                styleOf(text(document, "style", "")),
                strings(document.get("keywords")),
                Set.copyOf(strings(document.get("declarationKeywords"))),
                text(document, "lineComment", "//"),
                text(document, "importStatementSuffix", ""),
                Set.copyOf(strings(document.get("implicitPackages"))),
                strings(document.get("globalNames")),
                textsOf(document.get("receiverTypes")),
                listsOf(document.get("receiverExtras")));
    }

    private static SyntaxDescriptor.Style styleOf(String name) {
        for (SyntaxDescriptor.Style style : SyntaxDescriptor.Style.values()) {
            if (style.name().equals(name.toUpperCase(Locale.ROOT).replace('-', '_'))) {
                return style;
            }
        }
        throw new IllegalStateException("Unknown syntax style " + name);
    }

    private static String text(Map<String, Object> document, String key, String fallback) {
        return document.get(key) instanceof String value ? value : fallback;
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        return items.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private static Map<String, String> textsOf(Object value) {
        Map<String, String> texts = new LinkedHashMap<>();
        entriesOf(value).forEach((key, entry) -> {
            if (entry instanceof String text) {
                texts.put(key, text);
            }
        });
        return Map.copyOf(texts);
    }

    private static Map<String, List<String>> listsOf(Object value) {
        Map<String, List<String>> lists = new LinkedHashMap<>();
        entriesOf(value).forEach((key, entry) -> lists.put(key, strings(entry)));
        return Map.copyOf(lists);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> entriesOf(Object value) {
        return value instanceof Map<?, ?> entries ? (Map<String, Object>) entries : Map.of();
    }
}
