package fr.epistudio.epysia.net.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class NetReader {
    private static final int CONTINUATION_BIT = 0x80;
    private static final int PAYLOAD_MASK = 0x7F;
    private static final int PAYLOAD_BITS = 7;
    private static final int MAXIMUM_VARINT_BYTES = 5;

    private final ByteBuffer buffer;

    public NetReader(ByteBuffer buffer) {
        this.buffer = buffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    public static NetReader wrapping(byte[] source, int offset, int length) {
        return new NetReader(ByteBuffer.wrap(source, offset, length));
    }

    public ByteBuffer buffer() {
        return buffer;
    }

    public boolean hasRemaining() {
        return buffer.hasRemaining();
    }

    public int remaining() {
        return buffer.remaining();
    }

    public int readByte() {
        requireRemaining(Byte.BYTES);
        return buffer.get() & 0xFF;
    }

    public boolean readBoolean() {
        return readByte() != 0;
    }

    public Optional<MessageType> readMessageType() {
        return MessageType.fromCode(readByte());
    }

    public int readShort() {
        requireRemaining(Short.BYTES);
        return buffer.getShort() & 0xFFFF;
    }

    public int readInt() {
        requireRemaining(Integer.BYTES);
        return buffer.getInt();
    }

    public long readLong() {
        requireRemaining(Long.BYTES);
        return buffer.getLong();
    }

    public float readFloat() {
        requireRemaining(Float.BYTES);
        return buffer.getFloat();
    }

    public int readVarInt() {
        int result = 0;
        for (int index = 0; index < MAXIMUM_VARINT_BYTES; index++) {
            int piece = readByte();
            result |= (piece & PAYLOAD_MASK) << (PAYLOAD_BITS * index);
            if ((piece & CONTINUATION_BIT) == 0) {
                return result;
            }
        }
        throw new MalformedPacketException("Variable length integer longer than " + MAXIMUM_VARINT_BYTES + " bytes");
    }

    public int readSignedVarInt() {
        int encoded = readVarInt();
        return (encoded >>> 1) ^ -(encoded & 1);
    }

    public float readQuantizedFloat(float precision) {
        return readSignedVarInt() * precision;
    }

    public String readString() {
        int length = requireLength(readVarInt());
        requireRemaining(length);
        byte[] encoded = new byte[length];
        buffer.get(encoded);
        return new String(encoded, StandardCharsets.UTF_8);
    }

    public byte[] readSizedBytes() {
        int length = requireLength(readVarInt());
        requireRemaining(length);
        byte[] payload = new byte[length];
        buffer.get(payload);
        return payload;
    }

    public int readSizedBytesInto(byte[] destination) {
        int length = requireLength(readVarInt());
        if (length > destination.length) {
            throw new MalformedPacketException("Payload of " + length
                    + " bytes exceeds destination of " + destination.length);
        }
        buffer.get(destination, 0, length);
        return length;
    }

    public void skip(int byteCount) {
        requireRemaining(byteCount);
        buffer.position(buffer.position() + byteCount);
    }

    public int requireCount(int declaredCount, int byteCostEach) {
        if (declaredCount < 0 || (long) declaredCount * byteCostEach > buffer.remaining()) {
            throw new MalformedPacketException("Packet declares " + declaredCount
                    + " entries of at least " + byteCostEach + " bytes but only "
                    + buffer.remaining() + " remain");
        }
        return declaredCount;
    }

    private int requireLength(int byteCount) {
        requireRemaining(byteCount);
        return byteCount;
    }

    private void requireRemaining(int byteCount) {
        if (byteCount < 0 || byteCount > buffer.remaining()) {
            throw new MalformedPacketException("Packet declares " + byteCount
                    + " bytes but only " + buffer.remaining() + " remain");
        }
    }
}
