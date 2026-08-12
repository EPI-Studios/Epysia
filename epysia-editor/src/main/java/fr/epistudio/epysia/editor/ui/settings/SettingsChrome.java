package fr.epistudio.epysia.editor.ui.settings;

import fr.epistudio.epysia.i18n.TextKey;

public interface SettingsChrome {

    boolean skipWhileFiltering();

    void hint(TextKey key);

    float labelColumnWidth();

    boolean filtering();

    void row(String label, Runnable control);

    boolean toggleRow(String label, boolean value);

    boolean accepts(String label);

    float sliderWidth();
}
