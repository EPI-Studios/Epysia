package fr.epistudio.epysia.scripting;

public interface ScheduledAction {

    void cancel();

    boolean isPending();
}
