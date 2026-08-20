package fr.epistudio.epysia.i18n;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;

public final class I18n {
    public static final String IDENTIFIER_SEPARATOR = "###";

    private static final String BUNDLE_BASE_NAME = "i18n.messages";

    private static volatile TranslationState state =
            loadState(Language.ENGLISH);

    private I18n() {
        throw new UnsupportedOperationException(
                "This utility class cannot be instantiated."
        );
    }

    public static Language language() {
        return state.language();
    }

    public static Locale locale() {
        return state.locale();
    }

    public static void setLanguage(final Language language) {
        Objects.requireNonNull(
                language,
                "Language cannot be null."
        );

        if (state.language() == language) {
            return;
        }

        state = loadState(language);
    }

    public static String translate(final TextKey key) {
        Objects.requireNonNull(
                key,
                "Translation key cannot be null."
        );

        return translate(key.key());
    }

    public static String translate(
            final TextKey key,
            final Object... arguments
    ) {
        Objects.requireNonNull(
                key,
                "Translation key cannot be null."
        );

        return translate(key.key(), arguments);
    }

    public static String translate(final String key) {
        Objects.requireNonNull(
                key,
                "Translation key cannot be null."
        );

        return state.translations().getOrDefault(
                key,
                missingTranslation(key)
        );
    }

    public static String translate(
            final String key,
            final Object... arguments
    ) {
        Objects.requireNonNull(
                key,
                "Translation key cannot be null."
        );

        final String pattern = translate(key);

        if (arguments == null || arguments.length == 0) {
            return pattern;
        }

        return new MessageFormat(
                pattern,
                state.locale()
        ).format(arguments);
    }

    public static String plural(
            final TextKey baseKey,
            final long count
    ) {
        Objects.requireNonNull(
                baseKey,
                "Translation key cannot be null."
        );

        return translate(
                pluralKey(baseKey.key(), count),
                count
        );
    }

    private static String pluralKey(
            final String baseKey,
            final long count
    ) {
        final PluralCategory category = PluralRules.categoryOf(
                state.locale(),
                count
        );

        final String wanted = baseKey + "." + category.suffix();

        if (state.translations().containsKey(wanted)) {
            return wanted;
        }

        return baseKey + "." + PluralCategory.OTHER.suffix();
    }

    public static String label(
            final TextKey key,
            final String stableId
    ) {
        return label(
                key,
                stableId,
                new Object[0]
        );
    }

    public static String label(
            final TextKey key,
            final String stableId,
            final Object... arguments
    ) {
        Objects.requireNonNull(
                key,
                "Translation key cannot be null."
        );

        return label(
                key.key(),
                stableId,
                arguments
        );
    }

    public static String label(
            final String key,
            final String stableId
    ) {
        return label(
                key,
                stableId,
                new Object[0]
        );
    }

    public static String label(
            final String key,
            final String stableId,
            final Object... arguments
    ) {
        Objects.requireNonNull(
                key,
                "Translation key cannot be null."
        );

        Objects.requireNonNull(
                stableId,
                "ImGui identifier cannot be null."
        );

        if (stableId.isBlank()) {
            throw new IllegalArgumentException(
                    "ImGui identifier cannot be blank."
            );
        }

        return translate(key, arguments)
                + IDENTIFIER_SEPARATOR
                + stableId;
    }

    private static TranslationState loadState(
            final Language language
    ) {
        try {
            final ResourceBundle resourceBundle =
                    ResourceBundle.getBundle(
                            BUNDLE_BASE_NAME,
                            language.locale()
                    );

            final Map<String, String> translations =
                    new HashMap<>(resourceBundle.keySet().size());

            for (final String key : resourceBundle.keySet()) {
                translations.put(
                        key,
                        resourceBundle.getString(key)
                );
            }

            return new TranslationState(
                    language,
                    language.locale(),
                    Map.copyOf(translations)
            );
        } catch (final MissingResourceException exception) {
            throw new IllegalStateException(
                    "Unable to load translations for language: "
                            + language.name(),
                    exception
            );
        }
    }

    private static String missingTranslation(
            final String key
    ) {
        return "!" + key + "!";
    }

    private record TranslationState(
            Language language,
            Locale locale,
            Map<String, String> translations
    ) {
        private TranslationState {
            Objects.requireNonNull(
                    language,
                    "Language cannot be null."
            );

            Objects.requireNonNull(
                    locale,
                    "Locale cannot be null."
            );

            Objects.requireNonNull(
                    translations,
                    "Translations cannot be null."
            );
        }
    }
}