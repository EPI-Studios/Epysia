package fr.epistudio.epysia.steam;

import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureUsage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class SteamAvatarTextures {

    private final RenderBackend backend;
    private final SteamFriendsInfo friends;
    private final Map<String, TextureHandle> textures = new HashMap<>();

    public SteamAvatarTextures(RenderBackend backend, SteamFriendsInfo friends) {
        this.backend = backend;
        this.friends = friends;
    }

    public Optional<TextureHandle> of(long steamId, SteamAvatarSize size) {
        String key = steamId + "/" + size;
        TextureHandle cached = textures.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        return friends.avatar(steamId, size).map(avatar -> {
            TextureHandle handle = upload(avatar);
            textures.put(key, handle);
            return handle;
        });
    }

    public void forget(long steamId) {
        textures.entrySet().removeIf(entry -> {
            if (!entry.getKey().startsWith(steamId + "/")) {
                return false;
            }
            backend.destroy(entry.getValue());
            return true;
        });
        friends.forget(steamId);
    }

    public void dispose() {
        textures.values().forEach(backend::destroy);
        textures.clear();
    }

    private TextureHandle upload(SteamAvatar avatar) {
        TextureHandle handle = backend.createTexture(new TextureDescriptor(avatar.width(),
                avatar.height(), TextureFormat.SRGB8_ALPHA8, TextureUsage.SAMPLED));
        ByteBuffer pixels = ByteBuffer.allocateDirect(avatar.rgba().length)
                .order(ByteOrder.nativeOrder());
        pixels.put(avatar.rgba()).flip();
        backend.writeTexture(handle, pixels);
        return handle;
    }
}
