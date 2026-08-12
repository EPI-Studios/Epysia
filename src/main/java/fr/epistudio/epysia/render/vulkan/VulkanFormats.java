package fr.epistudio.epysia.render.vulkan;

import fr.epistudio.epysia.render.backend.IndexFormat;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureKind;
import fr.epistudio.epysia.render.backend.VertexFormat;
import org.lwjgl.vulkan.VK10;

public final class VulkanFormats {

    private VulkanFormats() {
    }

    public static int of(TextureFormat format) {
        return switch (format) {
            case RGBA8 -> VK10.VK_FORMAT_R8G8B8A8_UNORM;
            case SRGB8_ALPHA8 -> VK10.VK_FORMAT_R8G8B8A8_SRGB;
            case RGBA16F -> VK10.VK_FORMAT_R16G16B16A16_SFLOAT;
            case R11G11B10F -> VK10.VK_FORMAT_B10G11R11_UFLOAT_PACK32;
            case R16F -> VK10.VK_FORMAT_R16_SFLOAT;
            case R32F -> VK10.VK_FORMAT_R32_SFLOAT;
            case DEPTH32F -> VK10.VK_FORMAT_D32_SFLOAT;
            case DEPTH32F_STENCIL8 -> VK10.VK_FORMAT_D32_SFLOAT_S8_UINT;
        };
    }

    public static int aspectOf(TextureFormat format) {
        return switch (format) {
            case DEPTH32F -> VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
            case DEPTH32F_STENCIL8 -> VK10.VK_IMAGE_ASPECT_DEPTH_BIT | VK10.VK_IMAGE_ASPECT_STENCIL_BIT;
            default -> VK10.VK_IMAGE_ASPECT_COLOR_BIT;
        };
    }

    public static int bytesPerPixel(TextureFormat format) {
        return switch (format) {
            case RGBA8, SRGB8_ALPHA8, R11G11B10F, R32F, DEPTH32F -> 4;
            case RGBA16F -> 8;
            case R16F -> 2;
            case DEPTH32F_STENCIL8 -> 8;
        };
    }

    public static int imageTypeOf(TextureKind kind) {
        return kind == TextureKind.TEXTURE_3D ? VK10.VK_IMAGE_TYPE_3D : VK10.VK_IMAGE_TYPE_2D;
    }

    public static int viewTypeOf(TextureKind kind) {
        return switch (kind) {
            case TEXTURE_2D -> VK10.VK_IMAGE_VIEW_TYPE_2D;
            case TEXTURE_3D -> VK10.VK_IMAGE_VIEW_TYPE_3D;
            case CUBEMAP -> VK10.VK_IMAGE_VIEW_TYPE_CUBE;
            case ARRAY_2D -> VK10.VK_IMAGE_VIEW_TYPE_2D_ARRAY;
        };
    }

    public static int of(IndexFormat format) {
        return format == IndexFormat.UINT16 ? VK10.VK_INDEX_TYPE_UINT16 : VK10.VK_INDEX_TYPE_UINT32;
    }

    public static int of(VertexFormat format) {
        if (format.integer()) {
            return integerFormat(format);
        }
        return switch (format.componentCount()) {
            case 1 -> VK10.VK_FORMAT_R32_SFLOAT;
            case 2 -> VK10.VK_FORMAT_R32G32_SFLOAT;
            case 3 -> VK10.VK_FORMAT_R32G32B32_SFLOAT;
            default -> VK10.VK_FORMAT_R32G32B32A32_SFLOAT;
        };
    }

    private static int integerFormat(VertexFormat format) {
        if (format.wideInteger()) {
            return switch (format.componentCount()) {
                case 1 -> VK10.VK_FORMAT_R32_UINT;
                case 2 -> VK10.VK_FORMAT_R32G32_UINT;
                case 3 -> VK10.VK_FORMAT_R32G32B32_UINT;
                default -> VK10.VK_FORMAT_R32G32B32A32_UINT;
            };
        }
        return switch (format.componentCount()) {
            case 1 -> VK10.VK_FORMAT_R16_UINT;
            case 2 -> VK10.VK_FORMAT_R16G16_UINT;
            case 3 -> VK10.VK_FORMAT_R16G16B16_UINT;
            default -> VK10.VK_FORMAT_R16G16B16A16_UINT;
        };
    }
}
