package net.luminis.quic;

import net.luminis.quic.frame.*;
import net.luminis.quic.log.Logger;
import net.luminis.quic.packet.*;
import net.luminis.quic.send.SenderImpl;
import net.luminis.tls.handshake.TlsEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class QuicConnectionImplTest {

    private int onePto = 40;
    private QuicConnectionImpl connection;
    private SenderImpl sender;

    @BeforeEach
    void createObjectUnderTest() throws Exception {
        sender = mock(SenderImpl.class);
        when(sender.getPto()).thenReturn(onePto);
        connection = new NonAbstractQuicConnection();
    }

    @Test
    void whenClosingNormalPacketsAreNotProcessed() {
        // Given
        connection.immediateClose(EncryptionLevel.App);

        // When
        ShortHeaderPacket packet = spy(new ShortHeaderPacket(Version.getDefault(), new byte[0], new CryptoFrame()));
        connection.process(packet, Instant.now());

        // Then
        verify(packet, never()).accept(any(PacketProcessor.class), any(Instant.class));
    }

    @Test
    void whenClosingNormalPacketLeadsToSendingConnectionClose() {
        // Given
        connection.immediateClose(EncryptionLevel.App);
        clearInvocations(sender);

        // When
        ShortHeaderPacket packet = spy(new ShortHeaderPacket(Version.getDefault(), new byte[0], new CryptoFrame()));
        connection.processPacket(Instant.now(), packet);

        // Then
        verify(sender, atLeast(1)).send(argThat(f -> f instanceof ConnectionCloseFrame), any(EncryptionLevel.class), any(Consumer.class));
    }

    @Test
    void whenReceivingCloseOneCloseIsSend() {
        // When
        connection.handlePeerClosing(new ConnectionCloseFrame(Version.getDefault(), 0, null));

        // Then
        verify(sender, atLeast(1)).send(argThat(f -> f instanceof ConnectionCloseFrame), any(EncryptionLevel.class), any(Consumer.class));
    }

    @Test
    void whenReceivingCloseNormalPacketsAreNotProcessed() {
        // When
        connection.handlePeerClosing(new ConnectionCloseFrame(Version.getDefault(), 0, null));

        // When
        ShortHeaderPacket packet = spy(new ShortHeaderPacket(Version.getDefault(), new byte[0], new CryptoFrame()));
        connection.process(packet, Instant.now());

        // Then
        verify(packet, never()).accept(any(PacketProcessor.class), any(Instant.class));
    }

    @Test
    void afterThreePtoConnectionIsTerminated() throws Exception {
        // Given
        connection.immediateClose(EncryptionLevel.App);

        // When
        Thread.sleep(2 * onePto);
        assertThat(((NonAbstractQuicConnection) connection).terminated).isFalse();

        Thread.sleep(2 * onePto);

        // Then
        assertThat(((NonAbstractQuicConnection) connection).terminated).isTrue();
    }

    @Test
    void whenPeerClosingAfterThreePtoConnectionIsTerminated() throws Exception {
        // When
        connection.handlePeerClosing(new ConnectionCloseFrame(Version.getDefault(), 0, null));

        // When
        Thread.sleep(2 * onePto);
        assertThat(((NonAbstractQuicConnection) connection).terminated).isFalse();

        Thread.sleep(2 * onePto);

        // Then
        assertThat(((NonAbstractQuicConnection) connection).terminated).isTrue();
    }

    @Test
    void inClosingStateNumberOfConnectionClosePacketsSendShouldBeRateLimited() {
        // Given
        connection.immediateClose(EncryptionLevel.App);

        // When
        ShortHeaderPacket packet = new ShortHeaderPacket(Version.getDefault(), new byte[0], new CryptoFrame());
        for (int i = 0; i < 100; i++) {
            connection.processPacket(Instant.now(), packet);
        }

        // Then
        verify(sender, atMost(50)).send(argThat(f -> f instanceof ConnectionCloseFrame), any(EncryptionLevel.class), any(Consumer.class));

    }

    class NonAbstractQuicConnection extends QuicConnectionImpl {
        public boolean terminated;

        NonAbstractQuicConnection() {
            super(Version.getDefault(), Role.Server, null, mock(Logger.class));
            idleTimer = new IdleTimer(this, log);
        }

       @Override
        protected void terminate() {
            super.terminate();
            terminated = true;
        }

        @Override
        public void process(QuicFrame frame, QuicPacket packet, Instant timeReceived) {
        }

        @Override
        public void process(AckFrame ackFrame, QuicPacket packet, Instant timeReceived) {
        }

        @Override
        public void process(ConnectionCloseFrame connectionCloseFrame, QuicPacket packet, Instant timeReceived) {
        }

        @Override
        public void process(CryptoFrame cryptoFrame, QuicPacket packet, Instant timeReceived) {
        }

        @Override
        public void process(HandshakeDoneFrame handshakeDoneFrame, QuicPacket packet, Instant timeReceived) {
        }

        @Override
        public void process(MaxDataFrame maxDataFrame, QuicPacket packet, Instant timeReceived) {
        }

        @Override
        public void process(MaxStreamDataFrame maxStreamDataFrame, QuicPacket packet, Instant timeReceived) {
        }

        @Override
        public void process(MaxStreamsFrame maxStreamsFrame, QuicPacket packet, Instant timeReceived) {
        }

        @Override
        public void process(NewConnectionIdFrame newConnectionIdFrame, QuicPacket packet, Instant timeReceived) {
        }

        @Override
        public void process(PathChallengeFrame pathChallengeFrame, QuicPacket packet, Instant timeReceived) {
        }

        @Override
        public void process(RetireConnectionIdFrame retireConnectionIdFrame, QuicPacket packet, Instant timeReceived) {
        }

        @Override
        public void process(StreamFrame streamFrame, QuicPacket packet, Instant timeReceived) {
        }

        @Override
        public void process(InitialPacket packet, Instant time) {
        }

        @Override
        public void process(ShortHeaderPacket packet, Instant time) {
            processFrames(packet, time);
        }

        @Override
        public void process(VersionNegotiationPacket packet, Instant time) {
        }

        @Override
        public void process(HandshakePacket packet, Instant time) {
        }

        @Override
        public void process(RetryPacket packet, Instant time) {
        }

        @Override
        public void process(ZeroRttPacket packet, Instant time) {
        }

        @Override
        public void registerProcessor(FrameProcessor2<AckFrame> processor) {
        }

        @Override
        protected int getSourceConnectionIdLength() {
            return 0;
        }

        @Override
        public void abortConnection(Throwable error) {
        }

        @Override
        protected SenderImpl getSender() {
            return sender;
        }

        @Override
        protected TlsEngine getTlsEngine() {
            return null;
        }

        @Override
        protected GlobalAckGenerator getAckGenerator() {
            return mock(GlobalAckGenerator.class);
        }

        @Override
        public long getInitialMaxStreamData() {
            return 0;
        }

        @Override
        public int getMaxShortHeaderPacketOverhead() {
            return 0;
        }

        @Override
        public byte[] getSourceConnectionId() {
            return new byte[0];
        }

        @Override
        public byte[] getDestinationConnectionId() {
            return new byte[0];
        }
    }
}
