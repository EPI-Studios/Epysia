package com.meekdev.psyhou.game;

import com.meekdev.psyhou.dialogue.Dialogue;
import com.meekdev.psyhou.dialogue.DialogueComponent;
import com.meekdev.psyhou.dialogue.DialogueLoader;
import com.meekdev.psyhou.dialogue.DialogueSystem;
import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.StandaloneRunner;
import fr.epistudio.epysia.audio.AudioBuffer;
import fr.epistudio.epysia.audio.AudioBufferLoader;
import fr.epistudio.epysia.audio.AudioBus;
import fr.epistudio.epysia.audio.AudioSystem;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.text.Font;
import fr.epistudio.epysia.ui.UiAnchor;
import fr.epistudio.epysia.ui.UiCanvasComponent;
import fr.epistudio.epysia.ui.UiColor;
import fr.epistudio.epysia.ui.UiInputSystem;
import fr.epistudio.epysia.ui.UiLabel;
import fr.epistudio.epysia.ui.UiPanel;
import fr.epistudio.epysia.ui.UiRenderSystem;

public final class PsyhouMain {

    private static final String FONT_RESOURCE = "fonts/pmingliu-regular.ttf";
    private static final String FONT_NAME = "psyhou";
    private static final float FONT_PIXEL_HEIGHT = 24.0f;
    private static final String PROLOGUE_RESOURCE = "dialogue/prologue.dlg";
    private static final String CLICK_SOUND_RESOURCE = "sfx/click.wav";

    private PsyhouMain() {
    }

    public static void main(String[] arguments) {
        StandaloneRunner.runStandalone("PSYHOU", 1280, 720, PsyhouMain::populate);
    }

    private static void populate(EpysiaEngine engine, EngineServices services) {
        ShaderLoader shaderLoader = ShaderLoader.autoDetect();
        engine.addRenderSystem(new UiRenderSystem(shaderLoader, services.window(), engine));
        engine.addSystem(new UiInputSystem());
        AudioSystem audioSystem = engine.systems().get(AudioSystem.class);
        UiLabel dialogueLabel = new UiLabel()
                .setColor(UiColor.rgb(0.95f, 0.95f, 0.95f));
        dialogueLabel.setAnchor(UiAnchor.CENTER);
        Font psyhouFont = engine.fonts().load(FONT_NAME, FONT_RESOURCE, FONT_PIXEL_HEIGHT, SamplerFilter.NEAREST);
        dialogueLabel.setFont(psyhouFont);
        engine.scene().addGameObject(buildStage(dialogueLabel));
        Dialogue prologue = DialogueLoader.loadFromResource(PROLOGUE_RESOURCE);
        engine.scene().addGameObject(buildDialogueDriver(prologue));
        AudioBuffer tick = AudioBufferLoader.loadFromResource(CLICK_SOUND_RESOURCE);
        engine.addSystem(new DialogueSystem(dialogueLabel, psyhouFont, audioSystem, tick));
        audioSystem.mixer().setBusGain(AudioBus.SFX, 0.5f);
    }

    private static GameObject buildStage(UiLabel dialogueLabel) {
        GameObject stage = new GameObject("stage");
        UiCanvasComponent canvas = new UiCanvasComponent();
        UiPanel background = (UiPanel) new UiPanel()
                .setColor(UiColor.rgba(0.0f, 0.0f, 0.0f, 1.0f))
                .setAnchor(UiAnchor.TOP_LEFT)
                .setOffset(0.0f, 0.0f)
                .setSize(8192.0f, 8192.0f);
        canvas.root().addChild(background);
        canvas.root().addChild(dialogueLabel);
        stage.addComponent(canvas);
        return stage;
    }

    private static GameObject buildDialogueDriver(Dialogue dialogue) {
        GameObject driver = new GameObject("dialogue-driver");
        driver.addComponent(new DialogueComponent(dialogue));
        return driver;
    }
}
