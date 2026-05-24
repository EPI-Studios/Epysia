package com.meekdev.psyhou.dialogue;

public record DialogueLine(String text, int pauseBeforeMilliseconds) {

    public DialogueLine(String text) {
        this(text, 0);
    }
}
