package com.meekdev.psyhou.dialogue;

import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.audio.AudioBuffer;
import fr.epistudio.epysia.audio.AudioBus;
import fr.epistudio.epysia.audio.AudioSystem;
import fr.epistudio.epysia.audio.OneShotRequest;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.input.MouseButton;
import fr.epistudio.epysia.render.text.Font;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.ui.UiLabel;

public final class DialogueSystem implements GameSystem {

    private static final float DEFAULT_CHARACTER_INTERVAL_MILLISECONDS = 38.0f;
    private static final float PUNCTUATION_EXTRA_DELAY_MILLISECONDS = 160.0f;

    private final UiLabel label;
    private final Font font;
    private final AudioSystem audioSystem;
    private final AudioBuffer tickBuffer;
    private final float characterIntervalMilliseconds;
    private boolean previousMouseDown;

    public DialogueSystem(UiLabel label, Font font, AudioSystem audioSystem, AudioBuffer tickBuffer) {
        this(label, font, audioSystem, tickBuffer, DEFAULT_CHARACTER_INTERVAL_MILLISECONDS);
    }

    public DialogueSystem(UiLabel label, Font font, AudioSystem audioSystem, AudioBuffer tickBuffer,
                          float characterIntervalMilliseconds) {
        this.label = label;
        this.font = font;
        this.audioSystem = audioSystem;
        this.tickBuffer = tickBuffer;
        this.characterIntervalMilliseconds = characterIntervalMilliseconds;
    }

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        DialogueComponent component = findDialogue(scene);
        if (component == null || component.dialogueFinished()) {
            return;
        }
        float deltaMilliseconds = deltaTimeSeconds * 1000.0f;
        boolean clicked = detectClickEdge(input);
        runStateMachine(component, deltaMilliseconds, clicked);
        updateLabel(component);
    }

    private DialogueComponent findDialogue(Scene scene) {
        for (GameObject gameObject : scene.gameObjects()) {
            DialogueComponent component = gameObject.getComponent(DialogueComponent.class).orElse(null);
            if (component != null) {
                return component;
            }
        }
        return null;
    }

    private boolean detectClickEdge(InputState input) {
        boolean down = input.isMouseButtonDown(MouseButton.LEFT);
        boolean justPressed = down && !previousMouseDown;
        previousMouseDown = down;
        return justPressed;
    }

    private void runStateMachine(DialogueComponent component, float deltaMilliseconds, boolean clicked) {
        if (!component.revealStarted()) {
            advancePauseBeforeReveal(component, deltaMilliseconds, clicked);
            return;
        }
        if (!component.lineFullyRevealed()) {
            if (clicked) {
                component.revealAll();
                return;
            }
            advanceTypewriter(component, deltaMilliseconds);
            return;
        }
        if (clicked) {
            component.advanceToNextLine();
        }
    }

    private void advancePauseBeforeReveal(DialogueComponent component, float deltaMilliseconds, boolean clicked) {
        if (clicked) {
            component.beginRevealing();
            playTickIfLineJustStarted(component);
            return;
        }
        component.advancePauseTimer(deltaMilliseconds);
        if (component.pauseElapsedMilliseconds() >= component.currentLine().pauseBeforeMilliseconds()) {
            component.beginRevealing();
            playTickIfLineJustStarted(component);
        }
    }

    private void advanceTypewriter(DialogueComponent component, float deltaMilliseconds) {
        component.advanceCharacterTimer(deltaMilliseconds);
        String text = component.currentLine().text();
        while (component.characterTimerMilliseconds() >= currentCharacterDelay(component, text)
                && !component.lineFullyRevealed()) {
            component.revealNextCharacter();
        }
    }

    private float currentCharacterDelay(DialogueComponent component, String text) {
        int nextIndex = component.charactersRevealed();
        if (nextIndex <= 0 || nextIndex > text.length()) {
            return characterIntervalMilliseconds;
        }
        char previousCharacter = text.charAt(nextIndex - 1);
        if (previousCharacter == '.' || previousCharacter == '!' || previousCharacter == '?') {
            return characterIntervalMilliseconds + PUNCTUATION_EXTRA_DELAY_MILLISECONDS;
        }
        if (previousCharacter == ',' || previousCharacter == ';' || previousCharacter == ':') {
            return characterIntervalMilliseconds + PUNCTUATION_EXTRA_DELAY_MILLISECONDS * 0.5f;
        }
        return characterIntervalMilliseconds;
    }

    private void playTickIfLineJustStarted(DialogueComponent component) {
        if (!component.consumeLineJustStarted()) {
            return;
        }
        sizeLabelForCurrentLine(component);
        if (audioSystem == null || tickBuffer == null) {
            return;
        }
        audioSystem.playOneShot(new OneShotRequest()
                .setBuffer(tickBuffer)
                .setBus(AudioBus.SFX)
                .setGain(1.6f));
    }

    private void sizeLabelForCurrentLine(DialogueComponent component) {
        String fullText = component.currentLine().text();
        float fullWidth = font.measureWidth(fullText);
        label.setSize(fullWidth, font.pixelHeight());
    }

    private void updateLabel(DialogueComponent component) {
        if (!component.revealStarted()) {
            label.setText("");
            return;
        }
        String fullText = component.currentLine().text();
        int revealed = Math.min(component.charactersRevealed(), fullText.length());
        label.setText(fullText.substring(0, revealed));
    }
}
