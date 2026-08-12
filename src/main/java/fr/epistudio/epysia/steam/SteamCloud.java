package fr.epistudio.epysia.steam;

import com.codedisaster.steamworks.SteamException;
import com.codedisaster.steamworks.SteamRemoteStorage;
import com.codedisaster.steamworks.SteamRemoteStorageCallback;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class SteamCloud {

    private final SteamRemoteStorage storage;

    SteamCloud() {
        storage = new SteamRemoteStorage(new SteamRemoteStorageCallback() {
        });
    }

    public boolean write(String file, byte[] contents) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(contents.length);
        buffer.put(contents).flip();
        try {
            return storage.fileWrite(file, buffer);
        } catch (SteamException refused) {
            return false;
        }
    }

    public boolean writeText(String file, String contents) {
        return write(file, contents.getBytes(StandardCharsets.UTF_8));
    }

    public Optional<byte[]> read(String file) {
        if (!storage.fileExists(file)) {
            return Optional.empty();
        }
        int size = storage.getFileSize(file);
        if (size <= 0) {
            return Optional.of(new byte[0]);
        }
        return readInto(file, size);
    }

    private Optional<byte[]> readInto(String file, int size) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(size);
        try {
            int read = storage.fileRead(file, buffer);
            byte[] contents = new byte[Math.max(0, read)];
            buffer.get(contents, 0, contents.length);
            return Optional.of(contents);
        } catch (SteamException unreadable) {
            return Optional.empty();
        }
    }

    public Optional<String> readText(String file) {
        return read(file).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    public boolean exists(String file) {
        return storage.fileExists(file);
    }

    public boolean delete(String file) {
        return storage.fileDelete(file);
    }

    public int fileCount() {
        return storage.getFileCount();
    }

    void dispose() {
        storage.dispose();
    }
}
