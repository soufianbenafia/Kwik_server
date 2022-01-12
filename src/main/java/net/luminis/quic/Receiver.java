/*
 * Copyright © 2019, 2020, 2021 Peter Doornbosch
 *
 * This file is part of Kwik, an implementation of the QUIC protocol in Java.
 *
 * Kwik is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Kwik is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package net.luminis.quic;

import net.luminis.quic.log.Logger;
import org.pcap4j.core.*;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.UdpPacket;
import org.pcap4j.util.NifSelector;
import org.savarese.vserv.tcpip.UDPPacket;

import java.io.IOException;
import java.net.*;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Receives UDP datagrams on separate thread and queues them for asynchronous processing.
 */
public class Receiver {

    public static final int MAX_DATAGRAM_SIZE = 1500;

    private final DatagramSocket socket;
    private final Logger log;
    private final Consumer<Throwable> abortCallback;
    private final Thread receiverThread;
    private final BlockingQueue<RawPacket> receivedPacketsQueue;
    private volatile boolean isClosing = false;
    private volatile boolean changing = false;
    private static final String SNAPLEN_KEY = Receiver.class.getName() + ".snaplen";
    private static final int SNAPLEN = Integer.getInteger(SNAPLEN_KEY, 65536); // [bytes]
    private PcapHandle handle = null;
    private PcapNetworkInterface nif = null;

    public Receiver(DatagramSocket socket, Logger log, Consumer<Throwable> abortCallback) {
        this.socket = socket;
        this.log = log;
        this.abortCallback = abortCallback;

        receiverThread = new Thread(() -> run(), "receiver");
        receiverThread.setDaemon(true);
        receivedPacketsQueue = new LinkedBlockingQueue<>();


        try {
            nif = new NifSelector().selectNetworkInterface();
            handle = nif.openLive(SNAPLEN, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, 0);
            handle.setFilter("udp and dst port 443", BpfProgram.BpfCompileMode.OPTIMIZE);
            log.debug("Socket receive buffer size: " + socket.getReceiveBufferSize());
        } catch (PcapNativeException | IOException | NotOpenException e) {
            // Ignore
        }
    }

    public void start() {
        receiverThread.start();
    }

    public void shutdown() {
        isClosing = true;
        receiverThread.interrupt();
    }

    public RawPacket get() throws InterruptedException {
        return receivedPacketsQueue.take();
    }

    public boolean hasMore() {
        return !receivedPacketsQueue.isEmpty();
    }

    /**
     * Retrieves a received packet from the queue.
     *
     * @param timeout the wait timeout in seconds
     * @return
     * @throws InterruptedException
     */
    public RawPacket get(int timeout) throws InterruptedException {
        return receivedPacketsQueue.poll(timeout, TimeUnit.SECONDS);
    }

//    private void run() {
//        int counter = 0;
//
//        try {
//            while (!isClosing) {
//                byte[] receiveBuffer = new byte[MAX_DATAGRAM_SIZE];
//                DatagramPacket receivedPacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
//                try {
//                    socket.receive(receivedPacket);
//
//                    Instant timeReceived = Instant.now();
//                    RawPacket rawPacket = new RawPacket(receivedPacket, timeReceived, counter++);
//                    receivedPacketsQueue.add(rawPacket);
//                } catch (SocketTimeoutException timeout) {
//                    // Impossible, as no socket timeout set
//                } catch (SocketException socketError) {
//                    if (changing) {
//                        // Expected
//                        log.debug("Ignoring socket closed exception, because changing socket", socketError);
//                        changing = false;  // Don't do it again.
//                    } else {
//                        throw socketError;
//                    }
//                }
//            }
//
//            log.debug("Terminating receive loop");
//        } catch (IOException e) {
//            if (!isClosing) {
//                // This is probably fatal
//                log.error("IOException while receiving datagrams", e);
//                abortCallback.accept(e);
//            } else {
//                log.debug("closing receiver");
//            }
//        } catch (Throwable fatal) {
//            log.error("IOException while receiving datagrams", fatal);
//            abortCallback.accept(fatal);
//        }
//    }
    private void run() {
        int counter = 0;

        try {
            while (! isClosing) {
                //byte[] receiveBuffer = new byte[MAX_DATAGRAM_SIZE];
                Packet packet = handle.getNextPacket();

                if(packet != null) {
                    DatagramPacket receivedPacket= new DatagramPacket(packet.get(UdpPacket.class).getPayload().getRawData(),
                            packet.get(UdpPacket.class).getPayload().getRawData().length,
                            packet.get(IpV4Packet.class).getHeader().getSrcAddr(), packet.get(UdpPacket.class).getHeader().getSrcPort().valueAsInt());
                    Instant timeReceived = Instant.now();
                    RawPacket rawPacket = new RawPacket(receivedPacket, timeReceived, counter++,packet,nif);
                    receivedPacketsQueue.add(rawPacket);
                }
            }

            log.debug("Terminating receive loop");
        } catch (Throwable fatal) {
            log.error("IOException while receiving datagrams", fatal);
            abortCallback.accept(fatal);
        }
    }

//    public void changeAddress(DatagramSocket newSocket) {
//        DatagramSocket oldSocket = socket;
//        socket = newSocket;
//        changing = true;
//        oldSocket.close();
//    }
}
