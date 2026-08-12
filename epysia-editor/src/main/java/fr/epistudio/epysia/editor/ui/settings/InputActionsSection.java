package fr.epistudio.epysia.editor.ui.settings;

import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.editor.ui.TextFields;
import fr.epistudio.epysia.editor.ui.kit.Texts;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.input.KeyCode;
import fr.epistudio.epysia.input.MouseButton;
import fr.epistudio.epysia.input.action.InputAction;
import fr.epistudio.epysia.input.action.InputActions;
import fr.epistudio.epysia.input.action.InputBinding;
import imgui.ImGui;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InputActionsSection {

    private static final int ACTION_NAME_CAPACITY = 64;
    private static final float ACTION_NAME_FIELD_WIDTH = 200.0f;

    private final SettingsChrome chrome;
    private final ImString newActionName = new ImString(ACTION_NAME_CAPACITY);
    private final Map<Integer, ImString> actionNameEditors = new HashMap<>();

    private List<InputAction> inputActions = new ArrayList<>(InputActions.defaultActions());
    private int listeningAction = -1;
    private boolean listeningNegative;

    public InputActionsSection(SettingsChrome chrome) {
        this.chrome = chrome;
    }

    public void load(List<InputAction> actions) {
        inputActions = new ArrayList<>(actions);
        actionNameEditors.clear();
        listeningAction = -1;
    }

    public List<InputAction> build() {
        return List.copyOf(inputActions);
    }

    public void render() {
        if (chrome.skipWhileFiltering()) {
            return;
        }
        chrome.hint(TextKey.EDITOR_SETTINGS_DIALOG_INPUT_ACTIONS_HELP);
        for (int index = 0; index < inputActions.size(); index++) {
            renderActionRows(index);
        }
        ImGui.separator();
        renderAddActionRow();
        if (inputActions.isEmpty()) {
            ImGui.textDisabled("No action yet. Name one above and bind it.");
        }
    }

    private void renderAddActionRow() {
        ImGui.setNextItemWidth(EditorScale.of(ACTION_NAME_FIELD_WIDTH));
        boolean submitted = TextFields.inputSubmitted("##new-action", newActionName);
        ImGui.sameLine();
        boolean pressed = ImGui.button(I18n.translate(TextKey.EDITOR_SETTINGS_DIALOG_ADD_ACTION));
        if ((pressed || submitted) && !newActionName.get().isBlank()) {
            addAction(newActionName.get());
            newActionName.set("");
        }
    }

    private void addAction(String desiredName) {
        inputActions.add(InputAction.button(InputActions.uniqueNameAmong(inputActions, desiredName)));
        actionNameEditors.clear();
        listeningAction = -1;
    }

    private void renderActionRows(int index) {
        InputAction action = inputActions.get(index);
        ImGui.pushID(index);
        ImGui.separator();
        renderActionHeader(index, action);
        renderBindingRow(index, action, false);
        renderBindingRow(index, action, true);
        ImGui.popID();
    }

    private void renderActionHeader(int index, InputAction action) {
        ImString editor = actionNameEditors.computeIfAbsent(index, key -> {
            ImString value = new ImString(ACTION_NAME_CAPACITY);
            value.set(action.name());
            return value;
        });
        ImGui.setNextItemWidth(EditorScale.of(ACTION_NAME_FIELD_WIDTH));
        if (ImGui.inputText("##name", editor)) {
            renameAction(index, editor.get());
        }
        ImGui.sameLine();
        if (ImGui.smallButton("Remove")) {
            removeAction(index);
        }
    }

    private void renameAction(int index, String requestedName) {
        InputAction action = inputActions.get(index);
        String trimmed = requestedName.trim();
        if (trimmed.isEmpty() || trimmed.equals(action.name())) {
            return;
        }
        List<InputAction> others = new ArrayList<>(inputActions);
        others.remove(index);
        inputActions.set(index, new InputAction(InputActions.uniqueNameAmong(others, trimmed),
                action.positive(), action.negative()));
    }

    private void removeAction(int index) {
        inputActions.remove(index);
        actionNameEditors.clear();
        listeningAction = -1;
    }

    private void renderBindingRow(int index, InputAction action, boolean negative) {
        List<InputBinding> bindings = negative ? action.negative() : action.positive();
        boolean listening = listeningAction == index && listeningNegative == negative;
        ImGui.pushID(negative ? "negative" : "positive");
        ImGui.alignTextToFramePadding();
        Texts.muted(negative ? "  negative" : "  positive");
        ImGui.sameLine(chrome.labelColumnWidth());
        ImGui.textUnformatted(listening ? "press a key or mouse button..." : describe(bindings));
        ImGui.sameLine();
        if (ImGui.smallButton(listening ? "Cancel" : "Rebind")) {
            listeningAction = listening ? -1 : index;
            listeningNegative = negative;
        }
        ImGui.popID();
        if (listening) {
            captureBinding(index, negative);
        }
    }

    private static String describe(List<InputBinding> bindings) {
        if (bindings.isEmpty()) {
            return "unbound";
        }
        StringBuilder text = new StringBuilder();
        for (InputBinding binding : bindings) {
            text.append(text.isEmpty() ? "" : ", ").append(binding.serialized());
        }
        return text.toString();
    }

    private void captureBinding(int index, boolean negative) {
        capturedBinding().ifPresent(binding -> {
            InputAction action = inputActions.get(index);
            List<InputBinding> replaced = List.of(binding);
            inputActions.set(index, negative
                    ? new InputAction(action.name(), action.positive(), replaced)
                    : new InputAction(action.name(), replaced, action.negative()));
            listeningAction = -1;
        });
    }

    private static Optional<InputBinding> capturedBinding() {
        for (MouseButton button : MouseButton.values()) {
            if (ImGui.isMouseClicked(button.ordinal())) {
                return Optional.of(InputBinding.mouse(button));
            }
        }
        for (KeyCode key : KeyCode.values()) {
            if (ImGui.isKeyPressed(key.glfwCode())) {
                return Optional.of(InputBinding.key(key));
            }
        }
        return Optional.empty();
    }
}
