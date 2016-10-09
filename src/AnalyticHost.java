import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/* An abstract host used to calculate latency and througput metrics of the network */
public abstract class AnalyticHost extends Host {

    public static final int INTEGER_BYTE_SIZE = Integer.SIZE / Byte.SIZE;

    static final int END_OF_TRANSMISSION = -2;
    static final int HEADER_SENTINEL = -1;
    static final int CLOSE_CONNECTION = 0;
    static final int ACK_REQUEST = 1;
    static final int ECHO_REQUEST = 2;
    static final int THROUGHPUT_METRICS_REQUEST = 3;

    static final int HEADER_SIZE = 1 + (Integer.SIZE / Byte.SIZE) * 3;

    /* Measures round-trip latency time with the remote host using a message of the given size */
    public long echoTest(int msgSize) throws Exception {
        if (!isConnectedToRemoteHost()) {
            System.out.println("No Connection Established with Remote Host");
            throw new IOException();
        }

        long startTime, endTime;

        //send header
        byte[] header = buildHeader(ECHO_REQUEST, msgSize, msgSize);
        try {
            sendMsg(header);
        } catch (IOException e) {
            System.out.println("Header Failed to Send");
            throw new IOException();
        }

        //build message
        byte[] msg = new byte[msgSize];
        rand.nextBytes(msg);

        //start clock and send message
        startTime = System.nanoTime();
        try {
            sendMsg(msg);
        } catch (IOException e) {
            System.out.println("Message Failed to Send");
            e.printStackTrace();
            throw new IOException();
        }

        //read echo and stop clock
        try {
            readMsg(msg);
        } catch (IOException e) {
            System.out.println("An Error occurred in reading the echoed message");
            throw new IOException();
        }
        endTime = System.nanoTime();

        return endTime - startTime;
    }

    /* Measures round-trip latency time with the remote host in both directions using messages of the given size.
     * Returns an list of the measured data which will be used to estimate throughput
   	*/
    public ArrayList<Long> throughputTest(int msgSize) throws Exception {
        if (!isConnectedToRemoteHost()) {
            System.out.println("No Connection Established with Remote Host");
            throw new IOException();
        }

        long startTime, endTime;
        ArrayList<Long> results = new ArrayList<Long>();

        //send header
        byte[] header = buildHeader(THROUGHPUT_METRICS_REQUEST, msgSize, msgSize);
        try {
            sendMsg(header);
        } catch (IOException e) {
            System.out.println("Header failed to send");
            throw new IOException();
        }

        //build message
        byte[] msg = new byte[msgSize];
        rand.nextBytes(msg);

        //start clock and send message
        startTime = System.nanoTime();
        try {
            sendMsg(msg);
        } catch (IOException e) {
            System.out.println("Message failed to send");
            throw new IOException();
        }

        //read ACK and stop clock
        try {
            readACK();
        } catch (IOException e) {
            System.out.println("An Error occurred in reading the ACK");
            throw new IOException();
        }
        endTime = System.nanoTime();

        //read echo and send ACK
        try {
            readMsg(msg);
            sendByte(ACK);
        } catch (IOException e) {
            System.out.println("An Error occurred in responding to echo");
            throw new IOException();
        }

        //read results from server
        long serverResults;
        try {
            serverResults = readLong();
        } catch (IOException e) {
            System.out.println("An Error occurred in reading the server results");
            throw new IOException();
        }

        results.add(endTime - startTime);
        results.add(serverResults);

        return results;
    }

    protected boolean reply() throws IOException {
        int requestCode;
        int totalByteCount;
        int msgSize;
        try {
            byte[] header = readHeader();
            requestCode = header[INTEGER_BYTE_SIZE];
            totalByteCount = ByteConverter.byteArrayToInt(Arrays.copyOfRange(header, 5, 9));
            msgSize = ByteConverter.byteArrayToInt(Arrays.copyOfRange(header, 9, header.length));
        } catch (IOException e) {
            System.out.println("Error Reading Header");
            throw new IOException();
        }

        if (requestCode == CLOSE_CONNECTION) {
            System.out.println("Closing connection");
            return false;
        }

        for (int bytesReadIn = 0; bytesReadIn < totalByteCount; bytesReadIn += msgSize) {
            int unreadBytes = totalByteCount - bytesReadIn;
            int currentMsgSize = unreadBytes >= msgSize ? msgSize : unreadBytes;
            byte[] msg = new byte[currentMsgSize];

            try {
                readMsg(msg);
            } catch (IOException e) {
                System.out.println("Error Reading Packet");
                throw new IOException();
            }

            switch (requestCode) {
                case ACK_REQUEST:
                    try {
                        sendByte(ACK);
                    } catch(IOException e) {
                        System.out.println("Failed to Respond to ACK Request");
                        throw new IOException();
                    }
                    break;
                case ECHO_REQUEST:
                    try {
                        sendMsg(msg);
                    } catch(IOException e) {
                        System.out.println("Failed to Respond to Echo Request");
                        throw new IOException();
                    }
                    break;
                case THROUGHPUT_METRICS_REQUEST:
                    long startTime;
                    try {
                        sendByte(ACK);
                        startTime = System.nanoTime();
                        sendMsg(msg);
                        readACK();
                        long endTime = System.nanoTime();
                        sendLong(endTime - startTime);
                    } catch(IOException e) {
                        System.out.println("Failed to Respond to Throughput Metric Request");
                        throw new IOException();
                    }
                    break;
                default:
                    System.out.println("Invalid request code: " + requestCode);
                    throw new IOException();
            }
        }
        return true;
    }

    public byte[] buildHeader(int requestCode, int msgSize, int packetSize) {
        byte[] header = new byte[HEADER_SIZE];

        byte[] headerSentinelAsBytes = ByteConverter.intToByteArray(HEADER_SENTINEL);
        System.arraycopy(headerSentinelAsBytes, 0, header, 0, headerSentinelAsBytes.length);
        header[INTEGER_BYTE_SIZE] = (byte)requestCode;
        byte[] msgSizeAsBytes = ByteConverter.intToByteArray(msgSize);
        byte[] packetSizeAsBytes = ByteConverter.intToByteArray(packetSize);

        System.arraycopy(msgSizeAsBytes, 0, header, 5, msgSizeAsBytes.length);
        System.arraycopy(packetSizeAsBytes, 0, header, 5 + msgSizeAsBytes.length, packetSizeAsBytes.length);

        return header;
    }

    public byte[] readHeader() throws IOException {
        byte[] header = new byte[HEADER_SIZE];
        int headerSentinel;
        do {
            readMsg(header);
            headerSentinel = ByteConverter.byteArrayToInt(Arrays.copyOfRange(header, 0, 4));
        } while (headerSentinel != HEADER_SENTINEL);

        return header;
    }
}