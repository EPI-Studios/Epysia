package fr.epistudio.epysia.render.vulkan;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

public final class SpirvDiskCache {

    private static final String DIRECTORY_PROPERTY = "epysia.vulkan.shaderCache";
    private static final String DIGEST_ALGORITHM = "SHA-256";

    private final Optional<Path> directory;

    public SpirvDiskCache() {
        this.directory = resolveDirectory();
    }

    public String keyFor(VulkanShaderStage stage, String source) {
        MessageDigest digest = newDigest();
        digest.update(stage.displayName().getBytes(StandardCharsets.UTF_8));
        digest.update(source.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }

    public Optional<byte[]> read(String key) {
        return directory.map(root -> root.resolve(key + ".spv"))
                .filter(Files::isRegularFile)
                .flatMap(SpirvDiskCache::readQuietly);
    }

    public void write(String key, byte[] spirv) {
        directory.ifPresent(root -> writeQuietly(root.resolve(key + ".spv"), spirv));
    }

    private static Optional<byte[]> readQuietly(Path path) {
        try {
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    private static void writeQuietly(Path path, byte[] spirv) {
        try {
            Files.write(path, spirv);
        } catch (IOException unwritable) {
            return;
        }
    }

    private static Optional<Path> resolveDirectory() {
        Path requested = Path.of(System.getProperty(DIRECTORY_PROPERTY,
                System.getProperty("user.home", ".") + "/.cache/epysia/spirv"));
        try {
            Files.createDirectories(requested);
            return Optional.of(requested);
        } catch (IOException uncreatable) {
            return Optional.empty();
        }
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(DIGEST_ALGORITHM);
        } catch (NoSuchAlgorithmException missing) {
            throw new EpysiaException("Missing " + DIGEST_ALGORITHM + " digest.", missing);
        }
    }
}
