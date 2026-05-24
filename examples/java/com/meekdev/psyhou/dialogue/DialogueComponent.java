package com.meekdev.psyhou.dialogue;

import fr.epistudio.epysia.components.Component;

public final class DialogueComponent extends Component {

    private final Dialogue dialogue;
    private int currentLineIndex;
    private float pauseElapsedMilliseconds;
    private boolean revealStarted;
    private int charactersRevealed;
    private float characterTimerMilliseconds;
    private boolean lineFullyRevealed;
    private boolean dialogueFinished;
    private boolean lineJustStarted;

    public DialogueComponent(Dialogue dialogue) {
        this.dialogue = dialogue;
    }

    public Dialogue dialogue() {
        return dialogue;
    }

    public int currentLineIndex() {
        return currentLineIndex;
    }

    public DialogueLine currentLine() {
        return dialogue.lines().get(currentLineIndex);
    }

    public float pauseElapsedMilliseconds() {
        return pauseElapsedMilliseconds;
    }

    public boolean revealStarted() {
        return revealStarted;
    }

    public int charactersRevealed() {
        return charactersRevealed;
    }

    public float characterTimerMilliseconds() {
        return characterTimerMilliseconds;
    }

    public boolean lineFullyRevealed() {
        return lineFullyRevealed;
    }

    public boolean dialogueFinished() {
        return dialogueFinished;
    }

    public boolean consumeLineJustStarted() {
        boolean was = lineJustStarted;
        lineJustStarted = false;
        return was;
    }

    public void advancePauseTimer(float deltaMilliseconds) {
        pauseElapsedMilliseconds += deltaMilliseconds;
    }

    public void beginRevealing() {
        revealStarted = true;
        lineJustStarted = true;
    }

    public void advanceCharacterTimer(float deltaMilliseconds) {
        characterTimerMilliseconds += deltaMilliseconds;
    }

    public void revealNextCharacter() {
        charactersRevealed++;
        characterTimerMilliseconds = 0.0f;
        if (charactersRevealed >= currentLine().text().length()) {
            lineFullyRevealed = true;
        }
    }

    public void revealAll() {
        charactersRevealed = currentLine().text().length();
        characterTimerMilliseconds = 0.0f;
        lineFullyRevealed = true;
    }

    public void advanceToNextLine() {
        if (currentLineIndex + 1 >= dialogue.lines().size()) {
            dialogueFinished = true;
            return;
        }
        currentLineIndex++;
        pauseElapsedMilliseconds = 0.0f;
        revealStarted = false;
        charactersRevealed = 0;
        characterTimerMilliseconds = 0.0f;
        lineFullyRevealed = false;
    }
}
