package fr.epistudio.epysia.scripting;

public interface Scheduler {
    void after(float seconds, Runnable action);
    void every(float seconds, Runnable action);
    void nextFrame(Runnable action);
}
