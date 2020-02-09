/*
 * Copyright © 2019 Peter Doornbosch
 *
 * This file is part of Kwik, a QUIC client Java library
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
package net.luminis.quic.stream;

import net.luminis.quic.*;
import net.luminis.quic.frame.MaxStreamsFrame;
import net.luminis.quic.frame.QuicFrame;
import net.luminis.quic.frame.StreamFrame;
import net.luminis.quic.log.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;


public class StreamManager implements FrameProcessor {

    private final Map<Integer, QuicStream> streams;
    private final Version quicVersion;
    private final QuicConnectionImpl connection;
    private FlowControl flowController;
    private final Logger log;
    private int nextStreamId;
    private Consumer<QuicStream> serverStreamCallback;
    private Long maxStreamsBidi;
    private Long maxStreamsUni;
    private long bidiStreams;
    private long uniStreams;


    public StreamManager(QuicConnectionImpl quicConnection, Logger log) {
        this.connection = quicConnection;
        this.log = log;
        quicVersion = Version.getDefault();
        streams = new ConcurrentHashMap<>();
    }

    public QuicStream createStream(boolean bidirectional) {
        synchronized (this) {
            if (bidirectional) {
                if (bidiStreams < maxStreamsBidi) {
                    bidiStreams++;
                } else {
                    return null;
                }
            }
            else {
                if (uniStreams < maxStreamsUni) {
                    uniStreams++;
                }
                else {
                    return null;
                }
            }
        }
        int streamId = generateClientStreamId(bidirectional);
        QuicStream stream = new QuicStream(quicVersion, streamId, connection, flowController, log);
        streams.put(streamId, stream);
        return stream;
    }

    private synchronized int generateClientStreamId(boolean bidirectional) {
        // https://tools.ietf.org/html/draft-ietf-quic-transport-17#section-2.1:
        // "0x0  | Client-Initiated, Bidirectional"
        int id = (nextStreamId << 2) + 0x00;
        if (! bidirectional) {
            // "0x2  | Client-Initiated, Unidirectional |"
            id += 0x02;
        }
        nextStreamId++;
        return id;
    }

    // TODO: inject FlowController in constructor (requires change in FlowController itself)
    public void setFlowController(FlowControl flowController) {
        this.flowController = flowController;
    }

    @Override
    public void process(QuicFrame frame, PnSpace pnSpace, Instant timeReceived) {
        if (frame instanceof StreamFrame) {
            process((StreamFrame) frame, pnSpace, timeReceived);
        }
        else if (frame instanceof MaxStreamsFrame) {
            process((MaxStreamsFrame) frame, pnSpace, timeReceived);
        }
        else {
            throw new IllegalArgumentException();  // Programming error
        }
    }

    public void process(StreamFrame frame, PnSpace pnSpace, Instant timeReceived) {
        int streamId = frame.getStreamId();
        QuicStream stream = streams.get(streamId);
        if (stream != null) {
            stream.add(frame);
        }
        else {
            if (streamId % 2 == 1) {
                // https://tools.ietf.org/html/draft-ietf-quic-transport-16#section-2.1
                // "servers initiate odd-numbered streams"
                log.info("Receiving data for server-initiated stream " + streamId);
                stream = new QuicStream(quicVersion, streamId, connection, null, log);
                streams.put(streamId, stream);
                stream.add((StreamFrame) frame);
                if (serverStreamCallback != null) {
                    serverStreamCallback.accept(stream);
                }
            }
            else {
                log.error("Receiving frame for non-existant stream " + streamId);
            }
        }
    }

    public synchronized void process(MaxStreamsFrame frame, PnSpace pnSpace, Instant timeReceived) {
        if (frame.isAppliesToBidirectional()) {
            if (frame.getMaxStreams() > maxStreamsBidi) {
                maxStreamsBidi = frame.getMaxStreams();
            }
        }
        else {
            if (frame.getMaxStreams() > maxStreamsUni) {
                maxStreamsUni = frame.getMaxStreams();
            }
        }
    }

    public void abortAll() {
        streams.values().stream().forEach(s -> s.abort());
    }

    public void setServerStreamCallback(Consumer<QuicStream> streamProcessor) {
        serverStreamCallback = streamProcessor;
    }

    public synchronized void setInitialMaxStreamsBidi(long initialMaxStreamsBidi) {
        if (maxStreamsBidi == null) {
            maxStreamsBidi = initialMaxStreamsBidi;
        }
        else {
            throw new IllegalStateException("initial max already set");
        }
    }

    public synchronized void setInitialMaxStreamsUni(long initialMaxStreamsUni) {
        if (maxStreamsUni == null) {
            maxStreamsUni = initialMaxStreamsUni;
        }
        else {
            throw new IllegalStateException("initial max already set");
        }
    }

    public long getMaxBidirectionalStreams() {
        return maxStreamsBidi;
    }

    public long getMaxUnirectionalStreams() {
        return maxStreamsUni;
    }
}

