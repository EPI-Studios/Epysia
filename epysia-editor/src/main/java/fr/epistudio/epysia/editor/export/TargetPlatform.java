package fr.epistudio.epysia.editor.export;

import java.nio.file.Path;

public enum TargetPlatform {
    WINDOWS("windows-x64", "Windows", ".exe", "", "app"),
    LINUX("linux-x64", "Linux", "", "bin", "lib/app");

    private final String identifier;
    private final String displayName;
    private final String launcherExtension;
    private final String launcherDirectory;
    private final String applicationDirectory;

    TargetPlatform(String identifier, String displayName, String launcherExtension,
                   String launcherDirectory, String applicationDirectory) {
        this.identifier = identifier;
        this.displayName = displayName;
        this.launcherExtension = launcherExtension;
        this.launcherDirectory = launcherDirectory;
        this.applicationDirectory = applicationDirectory;
    }

    public TemplateLayout layout(Path root, String launcherName) {
        Path application = resolve(root, applicationDirectory);
        Path launcher = resolve(root, launcherDirectory).resolve(launcherName + launcherExtension);
        Path config = application.resolve(launcherName + ".cfg");
        return new TemplateLayout(launcher, config, application);
    }

    private static Path resolve(Path root, String relative) {
        return relative.isEmpty() ? root : root.resolve(relative);
    }

    public String identifier() {
        return identifier;
    }

    public String displayName() {
        return displayName;
    }

    public String launcherExtension() {
        return launcherExtension;
    }
}
