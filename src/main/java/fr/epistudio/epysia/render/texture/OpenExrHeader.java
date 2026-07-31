package fr.epistudio.epysia.render.texture;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

final class OpenExrHeader {

    private static final int MAGIC = 0x01312F76;
    private static final int HEADER_BYTES = 4096;
    private static final String COMPRESSION_ATTRIBUTE = "compression";
    private static final List<String> COMPRESSION_NAMES = List.of(
            "NONE", "RLE", "ZIPS", "ZIP", "PIZ", "PXR24", "B44", "B44A", "DWAA", "DWAB");

    private OpenExrHeader() {
    }

    static Optional<String> compressionNameOf(String file) {
        return readHeader(file).flatMap(OpenExrHeader::compressionOf).map(OpenExrHeader::nameOf);
    }

    private static Optional<byte[]> readHeader(String file) {
        try (InputStream stream = Files.newInputStream(Path.of(file))) {
            byte[] header = stream.readNBytes(HEADER_BYTES);
            return header.length > 8 && littleEndianInt(header, 0) == MAGIC
                    ? Optional.of(header) : Optional.empty();
        } catch (IOException | InvalidPathException unreadable) {
            return Optional.empty();
        }
    }

    private static Optional<Integer> compressionOf(byte[] header) {
        int cursor = 8;
        while (cursor < header.length) {
            int nameEnd = terminatorAfter(header, cursor);
            String name = new String(header, cursor, nameEnd - cursor);
            if (name.isEmpty()) {
                return Optional.empty();
            }
            cursor = terminatorAfter(header, nameEnd + 1) + 1;
            int size = littleEndianInt(header, cursor);
            cursor += 4;
            if (name.equals(COMPRESSION_ATTRIBUTE) && size > 0 && cursor < header.length) {
                return Optional.of(Byte.toUnsignedInt(header[cursor]));
            }
            cursor += size;
        }
        return Optional.empty();
    }

    private static int terminatorAfter(byte[] header, int start) {
        int cursor = start;
        while (cursor < header.length && header[cursor] != 0) {
            cursor++;
        }
        return cursor;
    }

    private static String nameOf(int compression) {
        return compression >= 0 && compression < COMPRESSION_NAMES.size()
                ? COMPRESSION_NAMES.get(compression) : "type " + compression;
    }

    private static int littleEndianInt(byte[] bytes, int offset) {
        if (offset + 4 > bytes.length) {
            return 0;
        }
        return (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16)
                | ((bytes[offset + 3] & 0xFF) << 24);
    }
}
