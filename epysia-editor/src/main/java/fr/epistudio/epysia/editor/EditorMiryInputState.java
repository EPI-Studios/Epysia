package fr.epistudio.epysia.editor;

import com.miry.platform.InputConstants;
import com.miry.platform.MiryHost;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.input.KeyCode;
import fr.epistudio.epysia.input.MouseButton;

import java.util.EnumMap;
import java.util.Map;

public final class EditorMiryInputState implements InputState {

    private final MiryHost host;
    private final Map<KeyCode, Integer> keyMap;
    private final Map<MouseButton, Integer> mouseMap;

    public EditorMiryInputState(MiryHost host) {
        this.host = host;
        this.keyMap = buildKeyMap();
        this.mouseMap = buildMouseMap();
    }

    @Override
    public boolean isKeyDown(KeyCode key) {
        Integer miryCode = keyMap.get(key);
        if (miryCode == null) {
            return false;
        }
        return host.isKeyDown(miryCode);
    }

    @Override
    public boolean isMouseButtonDown(MouseButton button) {
        Integer miryCode = mouseMap.get(button);
        if (miryCode == null) {
            return false;
        }
        return host.isMouseDown(miryCode);
    }

    @Override
    public float cursorX() {
        return host.getMousePos().x;
    }

    @Override
    public float cursorY() {
        return host.getMousePos().y;
    }

    @Override
    public float scrollDeltaY() {
        return 0.0f;
    }

    private static Map<KeyCode, Integer> buildKeyMap() {
        Map<KeyCode, Integer> map = new EnumMap<>(KeyCode.class);
        map.put(KeyCode.W, InputConstants.KEY_W);
        map.put(KeyCode.A, InputConstants.KEY_A);
        map.put(KeyCode.S, InputConstants.KEY_S);
        map.put(KeyCode.D, InputConstants.KEY_D);
        map.put(KeyCode.SPACE, InputConstants.KEY_SPACE);
        map.put(KeyCode.LEFT_SHIFT, InputConstants.KEY_LEFT_SHIFT);
        map.put(KeyCode.LEFT_CONTROL, InputConstants.KEY_LEFT_CONTROL);
        map.put(KeyCode.RIGHT_SHIFT, InputConstants.KEY_RIGHT_SHIFT);
        map.put(KeyCode.RIGHT_CONTROL, InputConstants.KEY_RIGHT_CONTROL);
        map.put(KeyCode.ESCAPE, InputConstants.KEY_ESCAPE);
        return map;
    }

    private static Map<MouseButton, Integer> buildMouseMap() {
        Map<MouseButton, Integer> map = new EnumMap<>(MouseButton.class);
        map.put(MouseButton.LEFT, InputConstants.MOUSE_BUTTON_LEFT);
        map.put(MouseButton.RIGHT, InputConstants.MOUSE_BUTTON_RIGHT);
        map.put(MouseButton.MIDDLE, InputConstants.MOUSE_BUTTON_MIDDLE);
        return map;
    }
}
