package fr.epistudio.epysia.i18n;

import java.util.Locale;

public enum Language {

    ENGLISH(
            Locale.ENGLISH,
            "language.english"
    ),

    FRENCH(
            Locale.FRANCE,
            "language.french"
    );

    private final Locale locale;
    private final String displayNameKey;

    Language(
            final Locale locale,
            final String displayNameKey
    ) {
        this.locale = locale;
        this.displayNameKey = displayNameKey;
    }

    public Locale locale() {
        return locale;
    }

    public String displayNameKey() {
        return displayNameKey;
    }
}