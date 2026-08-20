package fr.epistudio.epysia.editor.langpack;

import fr.epistudio.epysia.scene.serialization.JsonReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record LanguagePackCatalogue(List<LanguagePack> packs) {

    private static final String PACKS_KEY = "packs";

    public LanguagePackCatalogue {
        packs = List.copyOf(packs);
    }

    public static LanguagePackCatalogue empty() {
        return new LanguagePackCatalogue(List.of());
    }

    public static LanguagePackCatalogue parse(String source) {
        Object listed = new JsonReader(source).readRootObject().get(PACKS_KEY);
        if (!(listed instanceof List<?> entries)) {
            return empty();
        }
        List<LanguagePack> parsed = new ArrayList<>();
        for (Object entry : entries) {
            if (entry instanceof Map<?, ?> fields) {
                packOf(fields).ifPresent(parsed::add);
            }
        }
        return new LanguagePackCatalogue(parsed);
    }

    private static Optional<LanguagePack> packOf(Map<?, ?> fields) {
        String identifier = text(fields, "id");
        String version = text(fields, "version");
        String archive = text(fields, "asset");
        if (identifier.isEmpty() || version.isEmpty() || archive.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new LanguagePack(identifier, text(fields, "name"),
                text(fields, "description"), version, archive, text(fields, "url"),
                number(fields, "sizeBytes"), text(fields, "sha256"),
                text(fields, "runtimeAsset"), text(fields, "runtimeUrl"),
                text(fields, "runtimeSha256")));
    }

    private static String text(Map<?, ?> fields, String key) {
        Object value = fields.get(key);
        return value == null ? "" : value.toString();
    }

    private static long number(Map<?, ?> fields, String key) {
        Object value = fields.get(key);
        return value instanceof Number counted ? counted.longValue() : 0L;
    }

    public boolean isEmpty() {
        return packs.isEmpty();
    }

    public Optional<LanguagePack> find(String identifier) {
        return packs.stream().filter(pack -> pack.identifier().equals(identifier)).findFirst();
    }
}
