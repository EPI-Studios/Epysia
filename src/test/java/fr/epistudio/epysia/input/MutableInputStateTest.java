package fr.epistudio.epysia.input;

import fr.epistudio.epysia.input.action.InputBinding;
import fr.epistudio.epysia.input.gamepad.GamepadAxis;
import fr.epistudio.epysia.input.gamepad.GamepadButton;
import org.junit.jupiter.api.Test;

import static org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutableInputStateTest {

    private static final int NO_MODIFIERS = 0;
    private static final float BUFFER_WINDOW_SECONDS = 0.2f;

    @Test
    void keepsATapThatStartsAndEndsInsideOnePollInterval() {
        MutableInputState input = new MutableInputState();

        input.onKey(KeyCode.SPACE, true, NO_MODIFIERS);
        input.onKey(KeyCode.SPACE, false, NO_MODIFIERS);

        assertTrue(input.wasKeyPressed(KeyCode.SPACE), "the press inside the interval must survive");
        assertTrue(input.wasKeyReleased(KeyCode.SPACE), "the release inside the interval must survive");
        assertFalse(input.isKeyDown(KeyCode.SPACE), "the key must end up released");
    }

    @Test
    void reportsAnEdgeOnlyForTheStepThatSawIt() {
        MutableInputState input = new MutableInputState();

        input.onKey(KeyCode.SPACE, true, NO_MODIFIERS);
        input.advanceFrame();

        assertFalse(input.wasKeyPressed(KeyCode.SPACE), "an edge must not repeat on the next step");
        assertTrue(input.isKeyDown(KeyCode.SPACE), "the held state must persist across steps");
    }

    @Test
    void ignoresARepeatedPollOfAHeldKey() {
        MutableInputState input = new MutableInputState();

        input.onKey(KeyCode.W, true, NO_MODIFIERS);
        input.advanceFrame();
        input.onKey(KeyCode.W, true, NO_MODIFIERS);

        assertFalse(input.wasKeyPressed(KeyCode.W), "polling a held key must not fake a new press");
    }

    @Test
    void accumulatesMouseMotionAcrossSeveralEvents() {
        MutableInputState input = new MutableInputState();

        input.onCursorPosition(100.0f, 100.0f);
        input.onCursorPosition(110.0f, 105.0f);
        input.onCursorPosition(115.0f, 95.0f);

        assertEquals(15.0f, input.mouseDeltaX(), 1.0e-6f, "horizontal motion must accumulate");
        assertEquals(-5.0f, input.mouseDeltaY(), 1.0e-6f, "vertical motion must accumulate");
    }

    @Test
    void discardsMotionAcrossACursorModeChange() {
        MutableInputState input = new MutableInputState();

        input.onCursorPosition(100.0f, 100.0f);
        input.advanceFrame();
        input.discardCursorBaseline();
        input.onCursorPosition(600.0f, 400.0f);

        assertEquals(0.0f, input.mouseDeltaX(), 1.0e-6f, "a cursor warp must not spike the delta");
    }

    @Test
    void buffersAPressForAShortWindowAndConsumesItOnce() {
        MutableInputState input = new MutableInputState();
        input.setTimeSeconds(10.0);

        input.onKey(KeyCode.SPACE, true, NO_MODIFIERS);
        input.onKey(KeyCode.SPACE, false, NO_MODIFIERS);
        input.advanceFrame();
        input.setTimeSeconds(10.1);

        assertTrue(input.consumeBufferedKeyPress(KeyCode.SPACE, BUFFER_WINDOW_SECONDS),
                "a press just before the step must still be claimable");
        assertFalse(input.consumeBufferedKeyPress(KeyCode.SPACE, BUFFER_WINDOW_SECONDS),
                "a buffered press must only be claimed once");
    }

    @Test
    void expiresABufferedPressOutsideTheWindow() {
        MutableInputState input = new MutableInputState();
        input.setTimeSeconds(10.0);

        input.onKey(KeyCode.SPACE, true, NO_MODIFIERS);
        input.setTimeSeconds(10.5);

        assertFalse(input.consumeBufferedKeyPress(KeyCode.SPACE, BUFFER_WINDOW_SECONDS),
                "a stale press must not be claimable");
    }

    @Test
    void recordsEveryEdgeInTheEventRingIncludingACollapsedDoubleTap() {
        MutableInputState input = new MutableInputState();

        input.onKey(KeyCode.SPACE, true, NO_MODIFIERS);
        input.onKey(KeyCode.SPACE, false, NO_MODIFIERS);
        input.onKey(KeyCode.SPACE, true, NO_MODIFIERS);
        input.onKey(KeyCode.SPACE, false, NO_MODIFIERS);

        assertEquals(4, input.recentEvents().size(),
                "the ring must keep both taps even though the latches collapse them");
    }

    @Test
    void exposesTypedTextAndClearsItEachStep() {
        MutableInputState input = new MutableInputState();

        input.onTextTyped('h');
        input.onTextTyped('i');

        assertEquals("hi", input.typedText(), "typed code points must accumulate in order");
        input.advanceFrame();
        assertEquals("", input.typedText(), "typed text must not leak into the next step");
    }

    @Test
    void separatesRepeatFromPress() {
        MutableInputState input = new MutableInputState();

        input.onKey(KeyCode.A, true, NO_MODIFIERS);
        input.advanceFrame();
        input.onKeyRepeat(KeyCode.A, NO_MODIFIERS);

        assertTrue(input.wasKeyRepeated(KeyCode.A), "a repeat must be visible");
        assertFalse(input.wasKeyPressed(KeyCode.A), "a repeat must not read as a fresh press");
    }

    @Test
    void requiresModifiersOnABindingThatAsksForThem() {
        MutableInputState input = new MutableInputState();
        InputBinding save = InputBinding.key(KeyCode.S, KeyModifier.CONTROL);

        input.onKey(KeyCode.S, true, NO_MODIFIERS);
        assertFalse(save.wasPressed(input), "the binding must not fire without its modifier");

        input.advanceFrame();
        input.onKey(KeyCode.S, false, NO_MODIFIERS);
        input.onKey(KeyCode.S, true, GLFW_MOD_CONTROL);
        assertTrue(save.wasPressed(input), "the binding must fire once its modifier is held");
    }

    @Test
    void roundTripsBindingsThroughTheirSerializedForm() {
        assertBindingRoundTrip(InputBinding.key(KeyCode.S, KeyModifier.CONTROL));
        assertBindingRoundTrip(InputBinding.mouse(MouseButton.LEFT));
        assertBindingRoundTrip(InputBinding.gamepadButton(GamepadButton.SOUTH, 2));
        assertBindingRoundTrip(InputBinding.gamepadAxis(GamepadAxis.LEFT_Y, 1, true));
    }

    private static void assertBindingRoundTrip(InputBinding binding) {
        String text = binding.serialized();
        InputBinding parsed = InputBinding.parse(text).orElseThrow();
        assertEquals(binding, parsed, "binding must survive its serialized form: " + text);
    }

    @Test
    void readsLegacyBindingStringsWithoutModifiers() {
        assertEquals(InputBinding.key(KeyCode.W), InputBinding.parse("key:W").orElseThrow(),
                "old scene data must keep parsing");
    }
}
