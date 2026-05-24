package fr.epistudio.epysia.editor;

import com.miry.core.Input;
import com.miry.platform.MiryContext;
import fr.epistudio.epysia.editor.launcher.LauncherLauncher;
import fr.epistudio.epysia.editor.project.Project;
import fr.epistudio.epysia.window.Window;
import org.lwjgl.opengl.GL;

public final class EditorLauncher {

    private static final String WINDOW_TITLE_PREFIX = "Epysia Editor: ";
    private static final int INITIAL_WINDOW_WIDTH = 1400;
    private static final int INITIAL_WINDOW_HEIGHT = 900;

    private EditorLauncher() {
    }

    public static void main(String[] arguments) {
        LauncherLauncher.main(arguments);
    }

    public static void runForProject(Project project) {
        Window window = new Window(WINDOW_TITLE_PREFIX + project.name(), INITIAL_WINDOW_WIDTH, INITIAL_WINDOW_HEIGHT);
        window.open();
        GL.createCapabilities();
        Input.init(window.handle());
        MiryContext.setHost(new EditorMiryHost(window));
        EditorApplication application = new EditorApplication(window, project);
        try {
            application.runLoop(window);
        } finally {
            window.close();
        }
    }
}
