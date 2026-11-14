package fr.epistudio.epysia.ui;

public interface UiTextMeasurer {

    UiTextMeasurer NONE = new UiTextMeasurer() {
        @Override
        public float measureTextWidth(String text, UiFontStyle style) {
            return 0.0f;
        }

        @Override
        public float lineHeight(UiFontStyle style) {
            return 0.0f;
        }
    };

    float measureTextWidth(String text, UiFontStyle style);

    float lineHeight(UiFontStyle style);
}
