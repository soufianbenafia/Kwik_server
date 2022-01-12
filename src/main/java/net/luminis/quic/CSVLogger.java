package net.luminis.quic;

import com.opencsv.CSVWriter;

import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class CSVLogger {

    int counter;

    InetSocketAddress ipAddressAndPortInitialPacket;

    InetSocketAddress ipAddressAndPortIWir;

    Instant timeReceivedInitialPacket;

    Instant timeStampRetrySend;

    Instant timeStampRetryAnswer;

    String tokenRetry;

    String tokenAnswerRetry;

    String SCIDInitialPacket;

    String SCIDRetryPacket;

    String DCIDRetryPacket;

    String SCIDRetryAnswer;

    String DCIDRetryAnswer;

    String validated;

    public void setTimeStampRetrySend(Instant timeStampRetrySend) {
        this.timeStampRetrySend = timeStampRetrySend;
    }

    public void setTimeStampRetryAnswer(Instant timeStampRetryAnswer) {
        this.timeStampRetryAnswer = timeStampRetryAnswer;
    }

    public void setIpAddressAndPortInitialPacket(InetSocketAddress ipAddressAndPortInitialPacket) {
        this.ipAddressAndPortInitialPacket = ipAddressAndPortInitialPacket;
    }

    public void setIpAddressAndPortIWir(InetSocketAddress ipAddressAndPortIWir) {
        this.ipAddressAndPortIWir = ipAddressAndPortIWir;
    }

    public void setTimeReceivedInitialPacket(Instant timeReceivedInitialPacket) {
        this.timeReceivedInitialPacket = timeReceivedInitialPacket;
    }

    public void setTokenRetry(String tokenRetry) {
        this.tokenRetry = tokenRetry;
    }

    public void setTokenAnswerRetry(String tokenAnswerRetry) {
        this.tokenAnswerRetry = tokenAnswerRetry;
    }

    public void setSCIDInitialPacket(String SCIDInitialPacket) {
        this.SCIDInitialPacket = SCIDInitialPacket;
    }

    public void setSCIDRetryPacket(String SCIDRetryPacket) {
        this.SCIDRetryPacket = SCIDRetryPacket;
    }

    public void setDCIDRetryPacket(String DCIDRetryPacket) {
        this.DCIDRetryPacket = DCIDRetryPacket;
    }

    public void setSCIDRetryAnswer(String SCIDRetryAnswer) {
        this.SCIDRetryAnswer = SCIDRetryAnswer;
    }

    public void setDCIDRetryAnswer(String DCIDRetryAnswer) {
        this.DCIDRetryAnswer = DCIDRetryAnswer;
    }

    public void setValidated(String validated) {
        this.validated = validated;
    }

    public void write() throws IOException {
        String[] entry;
        if (validated.equals("true")) {
            entry = new String[]{ipAddressAndPortInitialPacket.getAddress().getHostAddress(),
                    String.valueOf(ipAddressAndPortInitialPacket.getPort()),
                    String.valueOf(ipAddressAndPortIWir.getAddress()),
                    String.valueOf(ipAddressAndPortIWir.getPort()),
                    Timestamp.from(timeReceivedInitialPacket).toLocalDateTime().toString(),
                    Timestamp.from(timeStampRetrySend).toLocalDateTime().toString(),
                    Timestamp.from(timeStampRetryAnswer).toLocalDateTime().toString(),
                    ""+ ChronoUnit.MILLIS.between(Timestamp.from(timeStampRetrySend).toLocalDateTime(), Timestamp.from(timeStampRetryAnswer).toLocalDateTime()),
                    tokenRetry,
                    tokenAnswerRetry,
                    SCIDInitialPacket,
                    SCIDRetryPacket,
                    DCIDRetryPacket,
                    SCIDRetryAnswer,
                    DCIDRetryAnswer,
                    validated};
        } else {
            entry = new String[]{ipAddressAndPortInitialPacket.getAddress().getHostAddress(),
                    String.valueOf(ipAddressAndPortInitialPacket.getPort()),
                    String.valueOf(ipAddressAndPortIWir.getAddress()),
                    String.valueOf(ipAddressAndPortIWir.getPort()),
                    Timestamp.from(timeReceivedInitialPacket).toLocalDateTime().toString(),
                    Timestamp.from(timeStampRetrySend).toLocalDateTime().toString(),
                    "",
                    "NOT POSSIBLE",
                    tokenRetry,
                    tokenAnswerRetry,
                    SCIDInitialPacket,
                    SCIDRetryPacket,
                    DCIDRetryPacket,
                    SCIDRetryAnswer,
                    DCIDRetryAnswer,
                    validated};
        }
        String fileName = "entries.csv";
        CSVWriter writer = new CSVWriter(new FileWriter(fileName, true));
        if (counter == 0) {
            writer.writeNext(entry);
            counter++;
        }
        writer.close();
    }
}
