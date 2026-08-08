package fr.epistudio.epysia.render.text;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureUsage;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTPackContext;
import org.lwjgl.stb.STBTTPackRange;
import org.lwjgl.stb.STBTTPackedchar;
import org.lwjgl.stb.STBTruetype;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Font {
    public static final int ATLAS_SIZE = 1024;

    private static final String RESOURCE_ROOT = "src/main/resources";
    private static final long NO_ALLOCATOR = 0L;

    private final float pixelHeight;
    private final TextureHandle atlasTexture;
    private final List<GlyphRange> ranges;

    private record GlyphRange(int firstCodePoint, int count, STBTTPackedchar.Buffer glyphs) {
        boolean covers(int codePoint) {
            return codePoint >= firstCodePoint && codePoint < firstCodePoint + count;
        }
    }

    private Font(float pixelHeight, TextureHandle atlasTexture, List<GlyphRange> ranges) {
        this.pixelHeight = pixelHeight;
        this.atlasTexture = atlasTexture;
        this.ranges = ranges;
    }

    public static Font loadFromResource(RenderBackend backend, String relativePath, float pixelHeight) {
        return loadFromResource(backend, relativePath, pixelHeight, SamplerFilter.LINEAR);
    }

    public static Font loadFromResource(RenderBackend backend, String relativePath, float pixelHeight,
                                        SamplerFilter samplerFilter) {
        return bake(backend, readResource(relativePath), pixelHeight, samplerFilter);
    }

    public static Font bake(RenderBackend backend, ByteBuffer ttfData, float pixelHeight) {
        return bake(backend, ttfData, pixelHeight, SamplerFilter.LINEAR);
    }

    public static Font bake(RenderBackend backend, ByteBuffer ttfData, float pixelHeight,
                            SamplerFilter samplerFilter) {
        ByteBuffer coverage = BufferUtils.createByteBuffer(ATLAS_SIZE * ATLAS_SIZE);
        List<GlyphRange> ranges = packRanges(ttfData, pixelHeight, coverage);
        TextureHandle handle = backend.createTexture(new TextureDescriptor(ATLAS_SIZE, ATLAS_SIZE,
                TextureFormat.RGBA8, TextureUsage.SAMPLED, samplerFilter));
        backend.writeTexture(handle, expandToRgba(coverage));
        return new Font(pixelHeight, handle, ranges);
    }

    private static List<GlyphRange> packRanges(ByteBuffer ttfData, float pixelHeight, ByteBuffer coverage) {
        List<GlyphRange> ranges = new ArrayList<>();
        for (int[] span : GlyphCoverage.SPANS) {
            ranges.add(new GlyphRange(span[0], span[1], STBTTPackedchar.calloc(span[1])));
        }
        STBTTPackContext context = STBTTPackContext.calloc();
        if (!STBTruetype.stbtt_PackBegin(context, coverage, ATLAS_SIZE, ATLAS_SIZE, 0, 1, NO_ALLOCATOR)) {
            context.free();
            throw new EpysiaException("Failed to begin font packing.");
        }
        STBTruetype.stbtt_PackSetOversampling(context, 1, 1);
        STBTTPackRange.Buffer packRanges = describeRanges(ranges, pixelHeight);
        boolean packed = STBTruetype.stbtt_PackFontRanges(context, ttfData, 0, packRanges);
        STBTruetype.stbtt_PackEnd(context);
        packRanges.free();
        context.free();
        if (!packed) {
            throw new EpysiaException("Font atlas too small for the requested glyph coverage at "
                    + pixelHeight + " pixels.");
        }
        return List.copyOf(ranges);
    }

    private static STBTTPackRange.Buffer describeRanges(List<GlyphRange> ranges, float pixelHeight) {
        STBTTPackRange.Buffer buffer = STBTTPackRange.malloc(ranges.size());
        for (int index = 0; index < ranges.size(); index++) {
            GlyphRange range = ranges.get(index);
            buffer.get(index)
                    .font_size(pixelHeight)
                    .first_unicode_codepoint_in_range(range.firstCodePoint())
                    .array_of_unicode_codepoints(null)
                    .num_chars(range.count())
                    .chardata_for_range(range.glyphs());
        }
        return buffer;
    }

    private static ByteBuffer expandToRgba(ByteBuffer coverage) {
        ByteBuffer rgba = BufferUtils.createByteBuffer(ATLAS_SIZE * ATLAS_SIZE * 4);
        for (int index = 0; index < ATLAS_SIZE * ATLAS_SIZE; index++) {
            byte value = coverage.get(index);
            rgba.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put(value);
        }
        rgba.flip();
        return rgba;
    }

    public TextureHandle atlasTexture() {
        return atlasTexture;
    }

    public float pixelHeight() {
        return pixelHeight;
    }

    public boolean covers(int codePoint) {
        return rangeOf(codePoint) != null;
    }

    public boolean appendQuad(int codePoint, FloatBuffer penX, FloatBuffer penY, STBTTAlignedQuad quad) {
        GlyphRange range = rangeOf(codePoint);
        if (range == null) {
            return false;
        }
        STBTruetype.stbtt_GetPackedQuad(range.glyphs(), ATLAS_SIZE, ATLAS_SIZE,
                codePoint - range.firstCodePoint(), penX, penY, quad, false);
        return true;
    }

    public float measureWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0f;
        }
        float advance = 0.0f;
        for (int index = 0; index < text.length(); index++) {
            GlyphRange range = rangeOf(text.charAt(index));
            if (range != null) {
                advance += range.glyphs().get(text.charAt(index) - range.firstCodePoint()).xadvance();
            }
        }
        return advance;
    }

    private GlyphRange rangeOf(int codePoint) {
        for (GlyphRange range : ranges) {
            if (range.covers(codePoint)) {
                return range;
            }
        }
        return null;
    }

    public void destroy(RenderBackend backend) {
        backend.destroy(atlasTexture);
        for (GlyphRange range : ranges) {
            range.glyphs().free();
        }
    }

    public static ByteBuffer readClasspathFont(String relativePath) {
        return readResource(relativePath);
    }

    private static ByteBuffer readResource(String relativePath) {
        Path absolute = Path.of(RESOURCE_ROOT).resolve(relativePath);
        if (Files.isRegularFile(absolute)) {
            return readFromFilesystem(absolute);
        }
        return readFromClasspath(relativePath);
    }

    private static ByteBuffer readFromFilesystem(Path absolute) {
        try {
            byte[] bytes = Files.readAllBytes(absolute);
            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes).flip();
            return buffer;
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read font " + absolute + ": " + exception.getMessage());
        }
    }

    private static ByteBuffer readFromClasspath(String relativePath) {
        try (InputStream stream = Font.class.getClassLoader().getResourceAsStream(relativePath)) {
            if (stream == null) {
                throw new EpysiaException("Font resource not found: " + relativePath);
            }
            byte[] bytes = stream.readAllBytes();
            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes).flip();
            return buffer;
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read font " + relativePath + ": " + exception.getMessage());
        }
    }
}
