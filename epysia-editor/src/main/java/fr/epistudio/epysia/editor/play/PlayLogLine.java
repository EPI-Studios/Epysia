package fr.epistudio.epysia.editor.play;

public record PlayLogLine(Level level, String message) {

    public enum Level { INFO, WARN, ERROR, SYSTEM }
}
