package fr.epistudio.epysia.assets.loaders;

import fr.epistudio.epysia.assets.AssetVariant;
import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureWrap;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public record TextureImportSettings(SamplerFilter filter, TextureWrap wrap, TextureFormat format,
                                    boolean mipmaps, int anisotropy) {

    public static final String FILTER_KEY = "filter";
    public static final String WRAP_KEY = "wrap";
    public static final String COLOR_SPACE_KEY = "colorSpace";
    public static final String MIPMAPS_KEY = "mipmaps";
    public static final String ANISOTROPY_KEY = "anisotropy";

    public static final int DEFAULT_ANISOTROPY = 8;
    public static final int MAXIMUM_ANISOTROPY = 16;

    public static final String FILTER_POINT = "point";
    public static final String FILTER_LINEAR = "linear";
    public static final String WRAP_REPEAT = "repeat";
    public static final String WRAP_CLAMP = "clamp";
    public static final String WRAP_MIRROR = "mirror";
    public static final String COLOR_SPACE_SRGB = "srgb";
    public static final String COLOR_SPACE_LINEAR = "linear";

    private static final String FILTER_PROPERTY = "epysia.texture.filter";

    public static TextureImportSettings from(Map<String, Object> meta, AssetVariant variant) {
        SamplerFilter filter = filterOf(setting(meta, variant, FILTER_KEY));
        boolean mipmaps = mipmapsOf(setting(meta, variant, MIPMAPS_KEY), filter);
        return new TextureImportSettings(
                filter,
                wrapOf(setting(meta, variant, WRAP_KEY)),
                formatOf(setting(meta, variant, COLOR_SPACE_KEY)),
                mipmaps,
                anisotropyOf(setting(meta, variant, ANISOTROPY_KEY), mipmaps));
    }

    private static boolean mipmapsOf(Optional<String> declared, SamplerFilter filter) {
        return declared.map(Boolean::parseBoolean).orElse(filter == SamplerFilter.LINEAR);
    }

    private static int anisotropyOf(Optional<String> declared, boolean mipmaps) {
        if (!mipmaps) {
            return 1;
        }
        return declared.map(TextureImportSettings::parseAnisotropy).orElse(DEFAULT_ANISOTROPY);
    }

    private static int parseAnisotropy(String declared) {
        try {
            return Math.clamp(Integer.parseInt(declared), 1, MAXIMUM_ANISOTROPY);
        } catch (NumberFormatException malformed) {
            return DEFAULT_ANISOTROPY;
        }
    }

    private static Optional<String> setting(Map<String, Object> meta, AssetVariant variant, String key) {
        return variant.value(key)
                .or(() -> Optional.ofNullable(meta.get(key))
                        .filter(String.class::isInstance)
                        .map(String.class::cast))
                .map(value -> value.toLowerCase(Locale.ROOT));
    }

    private static SamplerFilter filterOf(Optional<String> declared) {
        return declared
                .map(name -> isPoint(name) ? SamplerFilter.NEAREST : SamplerFilter.LINEAR)
                .orElseGet(TextureImportSettings::projectDefaultFilter);
    }

    private static boolean isPoint(String name) {
        return name.equals(FILTER_POINT) || name.equals("nearest");
    }

    private static SamplerFilter projectDefaultFilter() {
        return "nearest".equalsIgnoreCase(System.getProperty(FILTER_PROPERTY, FILTER_LINEAR))
                ? SamplerFilter.NEAREST : SamplerFilter.LINEAR;
    }

    private static TextureWrap wrapOf(Optional<String> declared) {
        return declared.map(name -> switch (name) {
            case WRAP_CLAMP -> TextureWrap.CLAMP_TO_EDGE;
            case WRAP_MIRROR -> TextureWrap.MIRRORED_REPEAT;
            default -> TextureWrap.REPEAT;
        }).orElse(TextureWrap.REPEAT);
    }

    private static TextureFormat formatOf(Optional<String> declared) {
        return declared.filter(COLOR_SPACE_SRGB::equals).isPresent()
                ? TextureFormat.SRGB8_ALPHA8 : TextureFormat.RGBA8;
    }
}
