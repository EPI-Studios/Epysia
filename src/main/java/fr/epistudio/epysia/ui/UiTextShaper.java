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
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer penX = stack.floats(x);
            FloatBuffer penY = stack.floats(y + font.pixelHeight());
            STBTTAlignedQuad quad = STBTTAlignedQuad.malloc(stack);
            for (int index = 0; index < text.length(); index++) {
                appendCharacter(drawList, font, text.charAt(index), x, penX, penY, quad, color);
            }
        }
    }

    private static void appendCharacter(UiDrawList drawList, Font font, char character, float startX,
                                        FloatBuffer penX, FloatBuffer penY, STBTTAlignedQuad quad,
                                        UiColor color) {
        if (character == '\n') {
            penX.put(0, startX);
            penY.put(0, penY.get(0) + font.pixelHeight() * LINE_SPACING);
            return;
        }
        if (!font.appendQuad(character, penX, penY, quad)) {
            return;
        }
        drawList.addQuadCorners(quad.x0(), quad.y0(), quad.x1(), quad.y1(),
                quad.s0(), quad.t0(), quad.s1(), quad.t1(), color);
    }
}
