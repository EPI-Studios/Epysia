package fr.epistudio.epysia.editor.assets;

import org.lwjgl.stb.STBImageWrite;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class IconComposer {

    private static final int CHANNELS = 4;
    private static final int CORNER_COUNT = 4;

    private IconComposer() {
    }

    public static void write(LoadedImage source, float[] cornerU, float[] cornerV, int outputSize,
                             Path target) throws IOException {
        ByteBuffer output = ByteBuffer.allocateDirect(outputSize * outputSize * CHANNELS);
        for (int y = 0; y < outputSize; y++) {
            for (int x = 0; x < outputSize; x++) {
                float alongX = (x + 0.5f) / outputSize;
                float alongY = (y + 0.5f) / outputSize;
                writePixel(source, output, interpolate(cornerU, alongX, alongY) * source.width() - 0.5f,
                        interpolate(cornerV, alongX, alongY) * source.height() - 0.5f);
            }
        }
        output.flip();
        Files.createDirectories(target.getParent());
        if (!STBImageWrite.stbi_write_png(target.toString(), outputSize, outputSize, CHANNELS,
                output, outputSize * CHANNELS)) {
            throw new IOException("stb refused to write " + target);
        }
    }

    private static float interpolate(float[] corners, float alongX, float alongY) {
        if (corners.length != CORNER_COUNT) {
            throw new IllegalArgumentException("A crop needs four corners, got " + corners.length);
        }
        float top = corners[0] + (corners[1] - corners[0]) * alongX;
        float bottom = corners[3] + (corners[2] - corners[3]) * alongX;
        return top + (bottom - top) * alongY;
    }

    private static void writePixel(LoadedImage source, ByteBuffer output, float sourceX, float sourceY) {
        int left = (int) Math.floor(sourceX);
        int top = (int) Math.floor(sourceY);
        float fractionX = sourceX - left;
        float fractionY = sourceY - top;
        for (int channel = 0; channel < CHANNELS; channel++) {
            float upper = blend(source.sample(left, top, channel),
                    source.sample(left + 1, top, channel), fractionX);
            float lower = blend(source.sample(left, top + 1, channel),
                    source.sample(left + 1, top + 1, channel), fractionX);
            output.put((byte) Math.round(blend(upper, lower, fractionY)));
        }
    }

    private static float blend(float first, float second, float amount) {
        return first + (second - first) * amount;
    }
}
