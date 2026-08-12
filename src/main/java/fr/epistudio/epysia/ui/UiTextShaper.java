package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.render.text.Font;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

public final class UiTextShaper {
    private static final float LINE_SPACING = 1.2f;

    private UiTextShaper() {
    }

    public static void appendText(UiDrawList drawList, Font font, String text,
                                  float x, float y, UiColor color) {
        appendText(drawList, font, text, x, y, 1.0f, color);
    }

    public static void appendText(UiDrawList drawList, Font font, String text,
                                  float x, float y, float magnification, UiColor color) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer penX = stack.floats(0.0f);
            FloatBuffer penY = stack.floats(font.pixelHeight());
            STBTTAlignedQuad quad = STBTTAlignedQuad.malloc(stack);
            for (int index = 0; index < text.length(); index++) {
                appendCharacter(drawList, font, text.charAt(index), penX, penY, quad,
                        x, y, magnification, color);
            }
        }
    }

    private static void appendCharacter(UiDrawList drawList, Font font, char character,
                                        FloatBuffer penX, FloatBuffer penY, STBTTAlignedQuad quad,
                                        float originX, float originY, float magnification, UiColor color) {
        if (character == '\n') {
            penX.put(0, 0.0f);
            penY.put(0, penY.get(0) + font.pixelHeight() * LINE_SPACING);
            return;
        }
        if (!font.appendQuad(character, penX, penY, quad)) {
            return;
        }
        drawList.addQuadCorners(
                originX + quad.x0() * magnification, originY + quad.y0() * magnification,
                originX + quad.x1() * magnification, originY + quad.y1() * magnification,
                quad.s0(), quad.t0(), quad.s1(), quad.t1(), color);
    }
}
