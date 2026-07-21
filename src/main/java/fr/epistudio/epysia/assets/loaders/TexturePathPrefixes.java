package fr.epistudio.epysia.assets.loaders;

import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureWrap;

public final class TexturePathPrefixes {

    public static final String SRGB_PREFIX = "srgb:";
    public static final String CLAMP_PREFIX = "clamp:";
    public static final String MIRROR_PREFIX = "mirror:";

    private TexturePathPrefixes() {
    }

    public record ParsedPath(TextureFormat format, TextureWrap wrap, String remainder) {
    }

    public static ParsedPath parse(String path) {
        TextureFormat format = TextureFormat.RGBA8;
        TextureWrap wrap = TextureWrap.REPEAT;
        String remainder = path;
        boolean prefixMatched = true;
        while (prefixMatched) {
            prefixMatched = false;
            if (remainder.startsWith(SRGB_PREFIX)) {
                format = TextureFormat.SRGB8_ALPHA8;
                remainder = remainder.substring(SRGB_PREFIX.length());
                prefixMatched = true;
            } else if (remainder.startsWith(CLAMP_PREFIX)) {
                wrap = TextureWrap.CLAMP_TO_EDGE;
                remainder = remainder.substring(CLAMP_PREFIX.length());
                prefixMatched = true;
            } else if (remainder.startsWith(MIRROR_PREFIX)) {
                wrap = TextureWrap.MIRRORED_REPEAT;
                remainder = remainder.substring(MIRROR_PREFIX.length());
                prefixMatched = true;
            }
        }
        return new ParsedPath(format, wrap, remainder);
    }

    public static String stripPrefixes(String path) {
        return parse(path).remainder();
    }
}
