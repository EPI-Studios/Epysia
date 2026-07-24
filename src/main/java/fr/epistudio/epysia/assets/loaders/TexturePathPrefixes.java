package fr.epistudio.epysia.assets.loaders;

import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureWrap;

import java.util.Optional;

public final class TexturePathPrefixes {

    public static final String SRGB_PREFIX = "srgb:";
    public static final String CLAMP_PREFIX = "clamp:";
    public static final String MIRROR_PREFIX = "mirror:";
    public static final String POINT_PREFIX = "point:";
    public static final String LINEAR_PREFIX = "linear:";

    private TexturePathPrefixes() {
    }

    public record ParsedPath(TextureFormat format, TextureWrap wrap, Optional<SamplerFilter> filter, String remainder) {
    }

    public static ParsedPath parse(String path) {
        TextureFormat format = TextureFormat.RGBA8;
        TextureWrap wrap = TextureWrap.REPEAT;
        Optional<SamplerFilter> filter = Optional.empty();
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
            } else if (remainder.startsWith(POINT_PREFIX)) {
                filter = Optional.of(SamplerFilter.NEAREST);
                remainder = remainder.substring(POINT_PREFIX.length());
                prefixMatched = true;
            } else if (remainder.startsWith(LINEAR_PREFIX)) {
                filter = Optional.of(SamplerFilter.LINEAR);
                remainder = remainder.substring(LINEAR_PREFIX.length());
                prefixMatched = true;
            }
        }
        return new ParsedPath(format, wrap, filter, remainder);
    }

    public static String stripPrefixes(String path) {
        return parse(path).remainder();
    }
}
