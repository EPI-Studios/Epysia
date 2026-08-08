package fr.epistudio.epysia.editor.export;

@FunctionalInterface
public interface ExportProgress {

    void report(ExportStage stage, float stageCompletion);
}
