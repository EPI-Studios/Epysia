package fr.epistudio.epysia.runtime;

public sealed interface RuntimeCommand
        permits RuntimeCommand.Pause,
                RuntimeCommand.Resume,
                RuntimeCommand.Quit {

    record Pause() implements RuntimeCommand {
    }

    record Resume() implements RuntimeCommand {
    }

    record Quit() implements RuntimeCommand {
    }
}
