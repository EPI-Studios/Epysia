package fr.epistudio.epysia.net.voice;

import fr.epistudio.epysia.net.protocol.NetReader;
import fr.epistudio.epysia.net.protocol.NetWriter;

public record VoiceFrame(int speakerPeer, int sequence, int channelId, byte[] payload) {
    public static final int SEQUENCE_MODULUS = 1 << 16;

    public void write(NetWriter writer) {
        writer.writeVarInt(speakerPeer);
        writer.writeShort(sequence);
        writer.writeVarInt(channelId);
        writer.writeSizedBytes(payload, 0, payload.length);
    }

    public static VoiceFrame read(NetReader reader) {
        int speakerPeer = reader.readVarInt();
        int sequence = reader.readShort();
        int channelId = reader.readVarInt();
        return new VoiceFrame(speakerPeer, sequence, channelId, reader.readSizedBytes());
    }

    public VoiceFrame withSpeaker(int peer) {
        return new VoiceFrame(peer, sequence, channelId, payload);
    }
}
