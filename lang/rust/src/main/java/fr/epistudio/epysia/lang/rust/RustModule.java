package fr.epistudio.epysia.lang.rust;

import fr.epistudio.epysia.scene.serialization.JsonReader;
import org.graalvm.polyglot.Value;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

final class RustModule {

    private final Value memory;
    private final Value alloc;
    private final Value dealloc;
    private final Value manifest;
    private final Value newInstance;
    private final Value read;
    private final Value write;
    private final Value onStart;
    private final Value onUpdate;
    private final Value onFixedUpdate;
    private final Value onDestroy;

    RustModule(Value exports) {
        this.memory = exports.getMember("memory");
        this.alloc = exports.getMember("epysia_alloc");
        this.dealloc = exports.getMember("epysia_dealloc");
        this.manifest = exports.getMember("epysia_manifest");
        this.newInstance = exports.getMember("epysia_new");
        this.read = exports.getMember("epysia_read");
        this.write = exports.getMember("epysia_write");
        this.onStart = exports.getMember("epysia_on_start");
        this.onUpdate = exports.getMember("epysia_on_update");
        this.onFixedUpdate = exports.getMember("epysia_on_fixed_update");
        this.onDestroy = exports.getMember("epysia_on_destroy");
    }

    Object manifest() {
        long packed = manifest.execute().asLong();
        return readAndFree(packed);
    }

    int newInstance(int index) {
        return newInstance.execute(index).asInt();
    }

    Object read(int handle, String name) {
        long[] nameBlob = writeBlob(name);
        long packed = read.execute(handle, (int) nameBlob[0], (int) nameBlob[1]).asLong();
        free(nameBlob[0], nameBlob[1]);
        return readAndFree(packed);
    }

    void write(int handle, String name, String valueJson) {
        long[] nameBlob = writeBlob(name);
        long[] valueBlob = writeBlob(valueJson);
        write.execute(handle, (int) nameBlob[0], (int) nameBlob[1], (int) valueBlob[0], (int) valueBlob[1]);
        free(nameBlob[0], nameBlob[1]);
        free(valueBlob[0], valueBlob[1]);
    }

    Object onStart(int handle, String transformJson) {
        long[] transformBlob = writeBlob(transformJson);
        long packed = onStart.execute(handle, (int) transformBlob[0], (int) transformBlob[1]).asLong();
        free(transformBlob[0], transformBlob[1]);
        return readAndFree(packed);
    }

    Object onUpdate(int handle, float deltaSeconds, String transformJson, String inputJson) {
        long[] transformBlob = writeBlob(transformJson);
        long[] inputBlob = writeBlob(inputJson);
        long packed = onUpdate.execute(handle, deltaSeconds,
                (int) transformBlob[0], (int) transformBlob[1], (int) inputBlob[0], (int) inputBlob[1]).asLong();
        free(transformBlob[0], transformBlob[1]);
        free(inputBlob[0], inputBlob[1]);
        return readAndFree(packed);
    }

    Object onFixedUpdate(int handle, float fixedStepSeconds, String transformJson) {
        long[] transformBlob = writeBlob(transformJson);
        long packed = onFixedUpdate
                .execute(handle, fixedStepSeconds, (int) transformBlob[0], (int) transformBlob[1]).asLong();
        free(transformBlob[0], transformBlob[1]);
        return readAndFree(packed);
    }

    void onDestroy(int handle) {
        onDestroy.execute(handle);
    }

    private long[] writeBlob(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        int pointer = alloc.execute(bytes.length).asInt();
        int index = 0;
        while (bytes.length - index >= Long.BYTES) {
            memory.writeBufferLong(ByteOrder.LITTLE_ENDIAN, pointer + index, packLong(bytes, index));
            index += Long.BYTES;
        }
        while (bytes.length - index >= Integer.BYTES) {
            memory.writeBufferInt(ByteOrder.LITTLE_ENDIAN, pointer + index, packInt(bytes, index));
            index += Integer.BYTES;
        }
        while (index < bytes.length) {
            memory.writeBufferByte(pointer + index, bytes[index]);
            index++;
        }
        return new long[] {pointer, bytes.length};
    }

    private Object readAndFree(long packed) {
        int pointer = (int) (packed >>> 32);
        int length = (int) packed;
        byte[] bytes = new byte[length];
        memory.readBuffer(pointer, bytes, 0, length);
        free(pointer, length);
        return new JsonReader(new String(bytes, StandardCharsets.UTF_8)).readValue();
    }

    private static long packLong(byte[] bytes, int offset) {
        long value = 0;
        for (int index = 0; index < Long.BYTES; index++) {
            value |= (bytes[offset + index] & 0xFFL) << (8 * index);
        }
        return value;
    }

    private static int packInt(byte[] bytes, int offset) {
        int value = 0;
        for (int index = 0; index < Integer.BYTES; index++) {
            value |= (bytes[offset + index] & 0xFF) << (8 * index);
        }
        return value;
    }

    private void free(long pointer, long length) {
        dealloc.execute((int) pointer, (int) length);
    }
}
