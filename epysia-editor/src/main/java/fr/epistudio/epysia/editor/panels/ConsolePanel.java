package fr.epistudio.epysia.editor.panels;

import com.miry.ui.PanelContext;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.widgets.Button;
import com.miry.ui.widgets.ConsoleLog;
import fr.epistudio.epysia.editor.EditorStyle;
import fr.epistudio.epysia.logging.Logger;

public final class ConsolePanel extends Panel implements Logger {

    private static final String TITLE = "Console";
    private static final int HEADER_HEIGHT = 24;
    private static final int CLEAR_BUTTON_WIDTH = 70;
    private static final int CLEAR_BUTTON_HEIGHT = 18;
    private static final int CONTENT_PADDING = 6;

    private final ConsoleLog consoleLog = new ConsoleLog();
    private final Button clearButton = new Button("Clear");

    public ConsolePanel() {
        super(TITLE);
    }

    @Override
    public void render(PanelContext context) {
        UiRenderer renderer = context.renderer();
        renderHeader(context);
        int contentTop = context.y() + HEADER_HEIGHT + CONTENT_PADDING;
        int contentHeight = Math.max(1, context.height() - HEADER_HEIGHT - CONTENT_PADDING * 2);
        consoleLog.render(renderer, context.ui().input(), context.ui().theme(),
                context.x() + CONTENT_PADDING, contentTop,
                context.width() - CONTENT_PADDING * 2, contentHeight);
    }

    private void renderHeader(PanelContext context) {
        UiRenderer renderer = context.renderer();
        renderer.drawRect(context.x(), context.y(), context.width(), HEADER_HEIGHT, EditorStyle.LEAF_HEADER_BG);
        renderer.drawText("Console", context.x() + 10, context.y() + 16, EditorStyle.COLOR_TEXT_HEADER);
        int clearX = context.x() + context.width() - CLEAR_BUTTON_WIDTH - 8;
        int clearY = context.y() + (HEADER_HEIGHT - CLEAR_BUTTON_HEIGHT) / 2;
        if (clearButton.render(renderer, context.uiContext(), context.ui().input(), context.ui().theme(),
                clearX, clearY, CLEAR_BUTTON_WIDTH, CLEAR_BUTTON_HEIGHT, true)) {
            consoleLog.clear();
        }
    }

    @Override
    public void info(String message) {
        consoleLog.info(message);
    }

    @Override
    public void warn(String message) {
        consoleLog.warning(message);
    }

    @Override
    public void error(String message) {
        consoleLog.error(message);
    }

    @Override
    public void error(String message, Throwable cause) {
        consoleLog.error(message + ": " + cause.getMessage());
    }
}
