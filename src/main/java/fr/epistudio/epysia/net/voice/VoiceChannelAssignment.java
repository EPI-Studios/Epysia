package fr.epistudio.epysia.net.voice;

public interface VoiceChannelAssignment {
    void assign(int peer, int channelId);

    int channelOf(int peer);
}
