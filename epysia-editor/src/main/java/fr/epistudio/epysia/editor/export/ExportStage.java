package fr.epistudio.epysia.editor.export;

public enum ExportStage {

    DOWNLOADING_TEMPLATE,
    UNPACKING_TEMPLATE,
    COPYING_TEMPLATE,
    COPYING_PROJECT,
    WRITING_LAUNCHER,
    ARCHIVING;

    public float overallCompletion(float stageCompletion) {
        return (ordinal() + Math.clamp(stageCompletion, 0.0f, 1.0f)) / values().length;
    }
}
