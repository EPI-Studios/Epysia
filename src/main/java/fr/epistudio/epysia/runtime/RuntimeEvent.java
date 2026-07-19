package fr.epistudio.epysia.runtime;

public sealed interface RuntimeEvent
        permits RuntimeEvent.Log,
                RuntimeEvent.FrameStats,
                RuntimeEvent.Ready,
                RuntimeEvent.Stopped {

    record Log(String level, String message, String stackTrace) implements RuntimeEvent {
        public static Log info(String message) {
            return new Log("INFO", message, "");
        }

        public static Log warn(String message) {
            return new Log("WARN", message, "");
        }

        public static Log error(String message, String stackTrace) {
            return new Log("ERROR", message, stackTrace == null ? "" : stackTrace);
        }
    }

    record FrameStats(float framesPerSecond, float frameMillis) implements RuntimeEvent {
    }

    record Ready(String windowTitle, int width, int height) implements RuntimeEvent {
    }

    record Stopped(String reason) implements RuntimeEvent {
    }
}
