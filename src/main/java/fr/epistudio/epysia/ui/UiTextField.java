package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import org.joml.Vector4f;

import fr.epistudio.epysia.input.KeyCode;

import java.util.function.Consumer;

@EpysiaComponent(name = "Ui Text Field", category = "UI",
        description = "Single line text entry with a caret and selection.")
public final class UiTextField extends UiElement {
    private static final float TEXT_PADDING = 6.0f;
    private static final float CARET_WIDTH = 1.5f;

    @Export(label = "Text")
    private String text = "";
    @Export(label = "Placeholder")
    private String placeholder = "";
    @Export(label = "Font", assetExtensions = {".ttf", ".otf"})
    private String fontPath = "";
    @Export(label = "Font size", min = 1.0f, max = 256.0f, step = 1.0f)
    private float fontSize = UiFontStyle.DEFAULT_SIZE;
    @Export(label = "Background", color = true)
    private final Vector4f backgroundColor = new Vector4f(0.1f, 0.11f, 0.14f, 1.0f);
    @Export(label = "Focused", color = true)
    private final Vector4f focusedColor = new Vector4f(0.14f, 0.16f, 0.22f, 1.0f);
    @Export(label = "Text color", color = true)
    private final Vector4f textColor = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
    @Export(label = "Placeholder color", color = true)
    private final Vector4f placeholderColor = new Vector4f(0.6f, 0.6f, 0.65f, 1.0f);

    private int caret;
    private boolean focused;
    private Consumer<String> onChanged = value -> {
    };
    private Consumer<String> onSubmit = value -> {
    };

    public String text() {
        return text;
    }

    public UiTextField setText(String value) {
        this.text = value == null ? "" : value;
        this.caret = text.length();
        return this;
    }

    public UiTextField setOnChanged(Consumer<String> listener) {
        this.onChanged = listener;
        return this;
    }

    public UiTextField setOnSubmit(Consumer<String> listener) {
        this.onSubmit = listener;
        return this;
    }

    public UiFontStyle fontStyle() {
        return UiFontStyle.of(fontPath, fontSize);
    }

    @Override
    public boolean interactive() {
        return true;
    }

    @Override
    public boolean wantsKeyboard() {
        return true;
    }

    @Override
    protected void paint(UiPainter painter) {
        UiRect rect = computedRect();
        painter.fillRect(rect, UiColors.of(focused ? focusedColor : backgroundColor));
        boolean empty = text.isEmpty();
        painter.drawText(rect.x() + TEXT_PADDING, rect.y() + TEXT_PADDING, empty ? placeholder : text,
                fontStyle(), UiColors.of(empty ? placeholderColor : textColor));
        if (focused) {
            paintCaret(painter, rect);
        }
    }

    private void paintCaret(UiPainter painter, UiRect rect) {
        UiFontStyle style = fontStyle();
        float advance = painter.measureTextWidth(text.substring(0, Math.min(caret, text.length())), style);
        painter.fillRect(new UiRect(rect.x() + TEXT_PADDING + advance, rect.y() + TEXT_PADDING,
                CARET_WIDTH, painter.lineHeight(style)), UiColors.of(textColor));
    }

    @Override
    protected void onFocusChanged(boolean value) {
        focused = value;
    }

    @Override
    protected void onText(String typed) {
        text = text.substring(0, caret) + typed + text.substring(caret);
        caret += typed.length();
        onChanged.accept(text);
    }

    @Override
    protected void onKey(KeyCode key) {
        switch (key) {
            case BACKSPACE -> deleteBefore();
            case DELETE -> deleteAfter();
            case ARROW_LEFT -> caret = Math.max(0, caret - 1);
            case ARROW_RIGHT -> caret = Math.min(text.length(), caret + 1);
            case ENTER -> onSubmit.accept(text);
            default -> {
            }
        }
    }

    private void deleteBefore() {
        if (caret == 0) {
            return;
        }
        text = text.substring(0, caret - 1) + text.substring(caret);
        caret--;
        onChanged.accept(text);
    }

    private void deleteAfter() {
        if (caret >= text.length()) {
            return;
        }
        text = text.substring(0, caret) + text.substring(caret + 1);
        onChanged.accept(text);
    }
}
