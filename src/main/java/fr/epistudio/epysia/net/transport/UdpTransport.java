package fr.epistudio.epysia.net.transport;

import fr.epistudio.epysia.net.protocol.MalformedPacketException;
import fr.epistudio.epysia.net.protocol.NetReader;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class UdpTransport implements Transport {
    static final int RELIABLE_HEADER_BYTES = 15;
    static final int UNRELIABLE_HEADER_BYTES = 1;
    private static final int RECEIVE_BUFFER_BYTES = 2048;
    private static final int DEFAULT_TRANSMISSION_UNIT = 1200;

    private final int transmissionUnit;
    private final ByteBuffer receiveBuffer = ByteBuffer.allocate(RECEIVE_BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
    private final Map<SocketAddress, Integer> connectionByAddress = new HashMap<>();
    private final Map<Integer, RemotePeer> peers = new HashMap<>();
    private DatagramChannel datagramChannel;
    private boolean acceptsNewConnections;
    private int nextConnection = 1;
    private long droppedOversizedDatagrams;

    public UdpTransport() {
        this(DEFAULT_TRANSMISSION_UNIT);
    }

    public UdpTransport(int transmissionUnit) {
        this.transmissionUnit = Math.max(RELIABLE_HEADER_BYTES + 1, transmissionUnit);
    }

    public long droppedOversizedDatagrams() {
        return droppedOversizedDatagrams;
    }

    @Override
    public void listen(int port) {
        openChannel(new InetSocketAddress(port));
        acceptsNewConnections = true;
    }

    @Override
    public int connect(String host, int port) {
        if (datagramChannel == null) {
            openChannel(new InetSocketAddress(0));
        }
        return registerPeer(new InetSocketAddress(host, port));
    }

    private void openChannel(InetSocketAddress bindAddress) {
        try {
            datagramChannel = DatagramChannel.open();
            datagramChannel.configureBlocking(false);
            datagramChannel.bind(bindAddress);
        } catch (IOException failure) {
            throw new TransportException("Cannot open datagram channel on " + bindAddress, failure);
        }
    }

    private int registerPeer(SocketAddress address) {
        Integer existing = connectionByAddress.get(address);
        if (existing != null) {
            return existing;
        }
        int connection = nextConnection++;
        RemotePeer peer = new RemotePeer(address);
        peer.assignConnection(connection);
        connectionByAddress.put(address, connection);
        peers.put(connection, peer);
        return connection;
    }

    @Override
    public void send(int connection, NetChannel channel, ByteBuffer payload) {
        RemotePeer peer = peers.get(connection);
        if (peer == null) {
            return;
        }
        if (channel == NetChannel.RELIABLE) {
            sendReliable(peer, payload);
            return;
        }
        sendUnreliable(peer, channel, payload);
    }

    private void sendUnreliable(RemotePeer peer, NetChannel channel, ByteBuffer payload) {
        int length = payload.remaining();
        if (length + UNRELIABLE_HEADER_BYTES > transmissionUnit) {
            droppedOversizedDatagrams++;
            return;
        }
        ByteBuffer datagram = ByteBuffer.allocate(length + UNRELIABLE_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        datagram.put((byte) channel.ordinal());
        datagram.put(payload.duplicate());
        transmit(peer, datagram.flip());
    }

    private void sendReliable(RemotePeer peer, ByteBuffer payload) {
        int chunkCapacity = transmissionUnit - RELIABLE_HEADER_BYTES;
        int total = payload.remaining();
        int fragmentCount = Math.max(1, (total + chunkCapacity - 1) / chunkCapacity);
        if (fragmentCount > RemotePeer.MAXIMUM_FRAGMENTS) {
            droppedOversizedDatagrams++;
            return;
        }
        ByteBuffer source = payload.duplicate();
        for (int index = 0; index < fragmentCount; index++) {
            int chunk = Math.min(chunkCapacity, source.remaining());
            byte[] datagram = buildReliableDatagram(peer, fragmentCount, index, source, chunk);
            peer.reliable().recordSent(readSequence(datagram), datagram);
            transmit(peer, ByteBuffer.wrap(datagram));
        }
        peer.clearPendingAcknowledgement();
    }

    private byte[] buildReliableDatagram(RemotePeer peer, int fragmentCount, int fragmentIndex,
                                         ByteBuffer source, int chunkLength) {
        ByteBuffer datagram = ByteBuffer.allocate(RELIABLE_HEADER_BYTES + chunkLength).order(ByteOrder.LITTLE_ENDIAN);
        datagram.put((byte) NetChannel.RELIABLE.ordinal());
        datagram.putInt(peer.reliable().allocateSequence());
        datagram.putInt(peer.reliable().ackUpTo());
        datagram.putInt(peer.reliable().ackBits());
        datagram.put((byte) fragmentCount);
        datagram.put((byte) fragmentIndex);
        byte[] chunk = new byte[chunkLength];
        source.get(chunk);
        datagram.put(chunk);
        return datagram.array();
    }

    private static int readSequence(byte[] datagram) {
        return ByteBuffer.wrap(datagram).order(ByteOrder.LITTLE_ENDIAN).getInt(1);
    }

    private void transmit(RemotePeer peer, ByteBuffer datagram) {
        try {
            datagramChannel.send(datagram, peer.address());
        } catch (IOException failure) {
            throw new TransportException("Datagram send to " + peer.address() + " failed", failure);
        }
    }

    @Override
    public void poll(TransportListener listener, float deltaTimeSeconds) {
        receiveAll(listener);
        for (RemotePeer peer : peers.values()) {
            resendDue(peer, deltaTimeSeconds);
            sendPendingAcknowledgement(peer);
        }
    }

    private void receiveAll(TransportListener listener) {
        SocketAddress source = receiveOne();
        while (source != null) {
            handleDatagram(listener, source);
            source = receiveOne();
        }
    }

    private SocketAddress receiveOne() {
        receiveBuffer.clear();
        try {
            return datagramChannel.receive(receiveBuffer);
        } catch (IOException failure) {
            throw new TransportException("Datagram receive failed", failure);
        }
    }

    private void handleDatagram(TransportListener listener, SocketAddress source) {
        receiveBuffer.flip();
        RemotePeer peer = resolvePeer(listener, source);
        if (peer == null || !receiveBuffer.hasRemaining()) {
            return;
        }
        NetChannel channel = NetChannel.fromOrdinal(receiveBuffer.get() & 0xFF).orElse(NetChannel.UNRELIABLE);
        if (channel == NetChannel.RELIABLE) {
            handleReliableDatagram(listener, peer);
            return;
        }
        byte[] payload = copyRemaining();
        listener.onPacketReceived(peer.connection(), channel, NetReader.wrapping(payload, 0, payload.length));
    }

    private RemotePeer resolvePeer(TransportListener listener, SocketAddress source) {
        Integer existing = connectionByAddress.get(source);
        if (existing != null) {
            return peers.get(existing);
        }
        if (!acceptsNewConnections) {
            return null;
        }
        int connection = registerPeer(source);
        listener.onConnectionOpened(connection);
        return peers.get(connection);
    }

    private void handleReliableDatagram(TransportListener listener, RemotePeer peer) {
        if (receiveBuffer.remaining() < RELIABLE_HEADER_BYTES - UNRELIABLE_HEADER_BYTES) {
            return;
        }
        int sequence = receiveBuffer.getInt();
        peer.reliable().acknowledge(receiveBuffer.getInt(), receiveBuffer.getInt());
        if (sequence == ReliableChannel.ACK_ONLY_SEQUENCE) {
            return;
        }
        peer.markPendingAcknowledgement();
        if (peer.reliable().accept(sequence, copyRemaining())) {
            deliverOrdered(listener, peer);
        }
    }

    private void deliverOrdered(TransportListener listener, RemotePeer peer) {
        for (byte[] framed : peer.reliable().drainInOrder()) {
            peer.assemble(framed).ifPresent(payload -> listener.onPacketReceived(
                    peer.connection(), NetChannel.RELIABLE, NetReader.wrapping(payload, 0, payload.length)));
        }
    }

    private byte[] copyRemaining() {
        byte[] payload = new byte[receiveBuffer.remaining()];
        receiveBuffer.get(payload);
        return payload;
    }

    private void resendDue(RemotePeer peer, float deltaTimeSeconds) {
        for (byte[] datagram : peer.reliable().datagramsDueForResend(deltaTimeSeconds)) {
            transmit(peer, ByteBuffer.wrap(datagram));
        }
    }

    private void sendPendingAcknowledgement(RemotePeer peer) {
        if (!peer.hasPendingAcknowledgement()) {
            return;
        }
        ByteBuffer datagram = ByteBuffer.allocate(RELIABLE_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        datagram.put((byte) NetChannel.RELIABLE.ordinal());
        datagram.putInt(ReliableChannel.ACK_ONLY_SEQUENCE);
        datagram.putInt(peer.reliable().ackUpTo());
        datagram.putInt(peer.reliable().ackBits());
        datagram.put((byte) 1);
        datagram.put((byte) 0);
        transmit(peer, datagram.flip());
        peer.clearPendingAcknowledgement();
    }

    @Override
    public void disconnect(int connection) {
        RemotePeer peer = peers.remove(connection);
        if (peer != null) {
            connectionByAddress.remove(peer.address());
        }
    }

    @Override
    public boolean isConnectionAlive(int connection) {
        return peers.containsKey(connection);
    }

    @Override
    public String addressOf(int connection) {
        RemotePeer peer = peers.get(connection);
        if (peer == null) {
            return "";
        }
        SocketAddress address = peer.address();
        if (address instanceof InetSocketAddress inet && inet.getAddress() != null) {
            return inet.getAddress().getHostAddress();
        }
        return address.toString();
    }

    @Override
    public void close() {
        peers.clear();
        connectionByAddress.clear();
        acceptsNewConnections = false;
        closeChannel();
    }

    private void closeChannel() {
        if (datagramChannel == null) {
            return;
        }
        try {
            datagramChannel.close();
        } catch (IOException failure) {
            throw new TransportException("Closing the datagram channel failed", failure);
        } finally {
            datagramChannel = null;
        }
    }

    private static final class RemotePeer {
        static final int MAXIMUM_FRAGMENTS = 255;

        private final SocketAddress address;
        private final ReliableChannel reliable = new ReliableChannel();
        private final List<byte[]> fragments = new ArrayList<>();
        private int connection;
        private boolean pendingAcknowledgement;

        private RemotePeer(SocketAddress address) {
            this.address = address;
        }

        private SocketAddress address() {
            return address;
        }

        private ReliableChannel reliable() {
            return reliable;
        }

        private int connection() {
            return connection;
        }

        private void assignConnection(int value) {
            this.connection = value;
        }

        private void markPendingAcknowledgement() {
            pendingAcknowledgement = true;
        }

        private void clearPendingAcknowledgement() {
            pendingAcknowledgement = false;
        }

        private boolean hasPendingAcknowledgement() {
            return pendingAcknowledgement;
        }

        private Optional<byte[]> assemble(byte[] framed) {
            if (framed.length < 2) {
                throw new MalformedPacketException("Reliable payload shorter than its fragment header");
            }
            int fragmentCount = framed[0] & 0xFF;
            int fragmentIndex = framed[1] & 0xFF;
            if (fragmentIndex == 0) {
                fragments.clear();
            }
            fragments.add(Arrays.copyOfRange(framed, 2, framed.length));
            if (fragmentIndex + 1 < fragmentCount) {
                return Optional.empty();
            }
            return Optional.of(concatenateFragments());
        }

        private byte[] concatenateFragments() {
            int total = 0;
            for (byte[] fragment : fragments) {
                total += fragment.length;
            }
            byte[] assembled = new byte[total];
            int cursor = 0;
            for (byte[] fragment : fragments) {
                System.arraycopy(fragment, 0, assembled, cursor, fragment.length);
                cursor += fragment.length;
            }
            fragments.clear();
            return assembled;
        }
    }
}
