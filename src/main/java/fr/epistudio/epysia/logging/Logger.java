package fr.epistudio.epysia.logging;

public interface Logger {

    void info(String message);

    void warn(String message);

    void error(String message);

    void error(String message, Throwable cause);
}
