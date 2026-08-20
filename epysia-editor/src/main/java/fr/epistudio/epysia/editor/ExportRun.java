package fr.epistudio.epysia.editor;

import fr.epistudio.epysia.editor.export.ExportRequest;
import fr.epistudio.epysia.editor.export.GameExporter;
import fr.epistudio.epysia.editor.export.TargetPlatform;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.gpu.GpuPreference;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.project.ProjectStore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public record ExportRun(Path projectDirectory, Path outputDirectory, String title,
                        String sceneFileName, TargetPlatform platform, Optional<Path> iconFile,
                        Optional<Path> templateArchive) {

    private static final String EXPORT_FLAG = "--export";
    private static final String PROJECT_FLAG = "--project";
    private static final String TITLE_FLAG = "--title";
    private static final String SCENE_FLAG = "--scene";
    private static final String PLATFORM_FLAG = "--platform";
    private static final String ICON_FLAG = "--icon";
    private static final String TEMPLATE_FLAG = "--template";

    public static Optional<ExportRun> parse(String[] arguments) {
        Optional<String> output = valueOf(arguments, EXPORT_FLAG);
        if (output.isEmpty()) {
            return Optional.empty();
        }
        String project = valueOf(arguments, PROJECT_FLAG)
                .orElseThrow(() -> new EpysiaException(EXPORT_FLAG + " also needs " + PROJECT_FLAG));
        return Optional.of(new ExportRun(Path.of(project), Path.of(output.get()),
                valueOf(arguments, TITLE_FLAG).orElse("Epysia Game"),
                valueOf(arguments, SCENE_FLAG).orElse("main.epyscene"),
                platformOf(valueOf(arguments, PLATFORM_FLAG).orElse("linux-x64")),
                valueOf(arguments, ICON_FLAG).map(Path::of),
                valueOf(arguments, TEMPLATE_FLAG).map(Path::of)));
    }

    public void run() throws IOException {
        Project project = new ProjectStore()
                .readProjectFromDisk(projectDirectory, System.currentTimeMillis())
                .orElseThrow(() -> new EpysiaException("No Epysia project at " + projectDirectory));
        ExportRequest request = new ExportRequest(outputDirectory, title,
                new ProjectStore().readRelease(project).version(), sceneFileName, platform,
                GpuPreference.fromId("system"), iconFile, templateArchive);
        Path result = new GameExporter(project).export(request,
                (stage, completion) -> System.out.println("[export] " + stage + " " + Math.round(completion * 100) + "%"));
        System.out.println("[export] wrote " + result);
    }

    private static TargetPlatform platformOf(String identifier) {
        for (TargetPlatform candidate : TargetPlatform.values()) {
            if (candidate.identifier().equals(identifier)) {
                return candidate;
            }
        }
        throw new EpysiaException("Unknown export platform " + identifier);
    }

    private static Optional<String> valueOf(String[] arguments, String flag) {
        for (int index = 0; index < arguments.length - 1; index++) {
            if (flag.equals(arguments[index])) {
                return Optional.of(arguments[index + 1]);
            }
        }
        return Optional.empty();
    }
}
