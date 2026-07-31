package fr.epistudio.epysia.render.texture;

import fr.epistudio.epysia.exceptions.EpysiaException;
import org.lwjgl.PointerBuffer;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyexr.TinyEXR;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Locale;

public record HighDynamicRangeImage(int width, int height, FloatBuffer pixels, boolean nativeAllocated) {

    private static final String RADIANCE_EXTENSION = ".hdr";
    private static final String OPEN_EXR_EXTENSION = ".exr";

    public static boolean isHighDynamicRange(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(RADIANCE_EXTENSION) || lower.endsWith(OPEN_EXR_EXTENSION);
    }

    public static HighDynamicRangeImage decodeRadiance(ByteBuffer encodedBytes) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            STBImage.stbi_set_flip_vertically_on_load(true);
            FloatBuffer pixels = STBImage.stbi_loadf_from_memory(encodedBytes, width, height, channels, 4);
            if (pixels == null) {
                throw new EpysiaException("Radiance decode failed: " + STBImage.stbi_failure_reason());
            }
            return new HighDynamicRangeImage(width.get(0), height.get(0), pixels, false);
        }
    }

    public static HighDynamicRangeImage decodeOpenExr(String file) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer out = stack.mallocPointer(1);
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            PointerBuffer error = stack.mallocPointer(1);
            int status = TinyEXR.nLoadEXRWithLayer(MemoryUtil.memAddress(out),
                    MemoryUtil.memAddress(width), MemoryUtil.memAddress(height),
                    MemoryUtil.memAddress(stack.UTF8(file)), MemoryUtil.NULL, MemoryUtil.memAddress(error));
            if (status != TinyEXR.TINYEXR_SUCCESS) {
                throw new EpysiaException("OpenEXR decode failed for " + file + ": " + messageOf(error)
                        + unsupportedCompressionHint(file));
            }
            FloatBuffer pixels = MemoryUtil.memFloatBuffer(out.get(0), width.get(0) * height.get(0) * 4);
            return new HighDynamicRangeImage(width.get(0), height.get(0), flipRows(pixels, width.get(0),
                    height.get(0)), true);
        }
    }

    private static String unsupportedCompressionHint(String file) {
        return OpenExrHeader.compressionNameOf(file)
                .map(name -> ". The file uses " + name + " compression, which the decoder does not support; "
                        + "re-encode it as ZIP or PIZ, or convert it to PNG.")
                .orElse("");
    }

    private static String messageOf(PointerBuffer error) {
        if (error.get(0) == MemoryUtil.NULL) {
            return "unknown error";
        }
        String message = MemoryUtil.memUTF8(error.get(0));
        TinyEXR.FreeEXRErrorMessage(MemoryUtil.memByteBuffer(error.get(0), message.length() + 1));
        return message;
    }

    private static FloatBuffer flipRows(FloatBuffer source, int width, int height) {
        FloatBuffer flipped = MemoryUtil.memAllocFloat(width * height * 4);
        int rowFloats = width * 4;
        for (int row = 0; row < height; row++) {
            int sourceRow = (height - 1 - row) * rowFloats;
            for (int index = 0; index < rowFloats; index++) {
                flipped.put(row * rowFloats + index, source.get(sourceRow + index));
            }
        }
        MemoryUtil.nmemFree(MemoryUtil.memAddress(source));
        return flipped;
    }

    public void free() {
        if (nativeAllocated) {
            MemoryUtil.memFree(pixels);
            return;
        }
        STBImage.stbi_image_free(pixels);
    }
}
