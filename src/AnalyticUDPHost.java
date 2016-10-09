import java.io.*;
import java.net.*;
import java.util.*;

public class AnalyticUDPHost extends AnalyticHost {
    public final static int MAX_MSG_SIZE = 512;
    public final static int PACKET_NUMBER_SIZE = INTEGER_BYTE_SIZE;
    public final static int MAX_PAYLOAD_SIZE = MAX_MSG_SIZE - PACKET_NUMBER_SIZE;
    public final static int LARGE_MESSAGE_SIZE = 64 * (int)Math.pow(2, 10);
    public final static int NEXT_PACKET_TIMEOUT = 10;

    DatagramSocket socket;
    InetAddress remoteAddress;
    int clientPort;
    int packetsReceived;
    int earlyTimeoutCount;

    public AnalyticUDPHost()
    {
        this.socket = null;
        this.rand = new Random();
        this.clientPort = -1;
    }

    public void connectToClient() throws Exception {
        this.packetsReceived = 0;
        this.earlyTimeoutCount = 0;
        this.socket = new DatagramSocket(PORT);

        byte[] data = new byte[1];
        DatagramPacket packet = new DatagramPacket(data, data.length);

        socket.receive(packet);
        this.remoteAddress = packet.getAddress();
        this.clientPort = packet.getPort();
    }

    public void connectToServer(String hostName) throws Exception {
        this.packetsReceived = 0;
        this.earlyTimeoutCount = 0;
        this.socket = new DatagramSocket();
        this.remoteAddress = InetAddress.getByName(hostName);

        sendByte(ACK);
    }

    @Override
    public void disconnectFromRemoteHost() throws IOException {
        byte[] header = buildHeader(CLOSE_CONNECTION, 0, 0);
        try {
            sendMsg(header);
        } catch (IOException e) {
            System.out.println("Header Failed to Send");
        }

        this.socket.close();
        this.socket = null;
        this.remoteAddress = null;
        this.clientPort = -1;
        this.packetsReceived = 0;
        this.earlyTimeoutCount = 0;
    }

    @Override
    public void sendByte(int data) throws IOException {
        byte[] bytePacket = new byte[1];
        bytePacket[0] = (byte)data;
        sendMsg(bytePacket);
    }

    /* For large messages, where we have a chance for packet loss, if this packet loss occurs the receiver will send a 
     * request for the lost frames and the sender will respond with the lost frames. This is important to note when 
     * caluclating throughput metrics as this delay will decrease the measured throughput values.
     */
    @Override
    public void sendMsg(byte[] data) throws IOException {
        if(!outputConnectionIsActive()) {
            System.out.println("Output Connection is inactive");
            throw new IOException();
        }
        int port = clientPort != -1 ? clientPort : PORT;

        if (data.length >= LARGE_MESSAGE_SIZE) {
            sendLargeMsg(data, port);
        }
        else {
            int packetCount = (int)Math.ceil((data.length * 1.0) / MAX_MSG_SIZE );
            int lastPacketNumber = packetCount - 1;
            for (int sentBytes = 0, packetNumber = 0; sentBytes < data.length; sentBytes += MAX_MSG_SIZE, packetNumber++) {
                int msgSize = packetNumber != lastPacketNumber ? MAX_MSG_SIZE : data.length - sentBytes;
                byte[] bytePacket = Arrays.copyOfRange(data, sentBytes, sentBytes + msgSize);
                DatagramPacket packet = new DatagramPacket(bytePacket, bytePacket.length, remoteAddress, port);
                socket.send(packet);
            }
        }
    }

    private void sendLargeMsg(byte[] data, int port) throws IOException {
        int packetCount = (int)Math.ceil((data.length * 1.0) / MAX_PAYLOAD_SIZE );
        ArrayList<Integer> packetsPending = new ArrayList<Integer>();
        for (int i = 0; i < packetCount; i++) {
            packetsPending.add(i);
        }

        int lastPacketNumber = packetCount - 1;
        int lastPayloadSize = data.length % MAX_PAYLOAD_SIZE;
        lastPayloadSize = lastPayloadSize > 0 ? lastPayloadSize : MAX_PAYLOAD_SIZE;

        while (packetsPending.size() > 0) {
            for (int packetNumber : packetsPending) {

                //pull the bytes to be sent in this packet from the msg data at large
                int msgSize = packetNumber != lastPacketNumber ? MAX_PAYLOAD_SIZE : lastPayloadSize;
                byte[] msg = new byte[msgSize];
                System.arraycopy(data, packetNumber * MAX_PAYLOAD_SIZE, msg, 0, msgSize);

                //adds the packet number to the front of the message
                byte[] bytePacket = new byte[msgSize + PACKET_NUMBER_SIZE];
                byte[] packetNumberAsBytes = ByteConverter.intToByteArray(packetNumber);
                System.arraycopy(packetNumberAsBytes, 0, bytePacket, 0, PACKET_NUMBER_SIZE);
                System.arraycopy(msg, 0, bytePacket, PACKET_NUMBER_SIZE, msgSize);

                //send the packet
                DatagramPacket packet = new DatagramPacket(bytePacket, bytePacket.length, remoteAddress, port);
                socket.send(packet);
            }

            //read in the number of packets that need to be resent
            byte[] resendPacketCountAsBytes = new byte[INTEGER_BYTE_SIZE];
            readMsg(resendPacketCountAsBytes);

            int resendPacketCount = ByteConverter.byteArrayToInt(resendPacketCountAsBytes);
            if (resendPacketCount == 0) {
                byte[] endOfMsg = ByteConverter.intToByteArray(END_OF_TRANSMISSION);
                sendMsg(endOfMsg);
                break;
            }
            else {
                //read in the list of packet numbers that need to be resent
                byte[] resendPacketArray = new byte[resendPacketCount * INTEGER_BYTE_SIZE];
                readMsg(resendPacketArray);
            }
        }
    }

    @Override
    public int readByte() throws IOException {
        byte[] data = new byte[1];
        readMsg(data);
        return data[0];
    }

    @Override
    public void readMsg(byte[] data) throws IOException {
        if(!inputConnectionIsActive()) {
            System.out.println("Input Connection is inactive");
            throw new IOException();
        }

        if (data.length >= LARGE_MESSAGE_SIZE) {
            readLargeMsg(data);
        }
        else {
            //split data into packets and send
            int packetCount = (int)Math.ceil((data.length * 1.0) / MAX_MSG_SIZE );
            int lastPacketNumber = packetCount - 1;
            for (int readBytes = 0, packetNumber = 0; readBytes < data.length; readBytes += MAX_MSG_SIZE, packetNumber++) {
                int msgSize = packetNumber != lastPacketNumber ? MAX_MSG_SIZE : data.length - readBytes;
                byte[] bytePacket = new byte[msgSize];
                DatagramPacket packet = new DatagramPacket(bytePacket, bytePacket.length);
                socket.receive(packet);
                System.arraycopy(bytePacket, 0, data, readBytes, msgSize);
            }
        }
    }

    private void readLargeMsg(byte[] data) throws IOException {
        int packetCount = (int)Math.ceil((data.length * 1.0)/ MAX_PAYLOAD_SIZE);

        ArrayList<Integer> packetsPending = new ArrayList<Integer>();
        for (int i = 0; i < packetCount; i++) {
            packetsPending.add(i);
        }

        int lastPacketNumber = packetCount - 1;
        int lastPayloadSize = data.length % MAX_PAYLOAD_SIZE;
        lastPayloadSize = lastPayloadSize > 0 ? lastPayloadSize : MAX_PAYLOAD_SIZE;

        while (packetsPending.size() > 0) {
            try {

                //receive packet
                byte[] wrappedMsg = new byte[MAX_MSG_SIZE];
                int packetNumber;
                DatagramPacket packet = new DatagramPacket(wrappedMsg, wrappedMsg.length);
                socket.receive(packet);

                //extract packet number and msg data
                packetNumber = getPacketNumber(wrappedMsg);
                int msgSize = packetNumber != lastPacketNumber ? MAX_PAYLOAD_SIZE : lastPayloadSize;
                byte[] msg = getMsgData(wrappedMsg, msgSize);

                //copy message payload to data array
                System.arraycopy(msg, 0, data, packetNumber * (MAX_PAYLOAD_SIZE), msg.length);

                //remove packet from pending list
                int packetNumberIndex = packetsPending.indexOf(packetNumber);
                try {
                    packetsPending.remove(packetNumberIndex);
                } catch (IndexOutOfBoundsException ignored) {

                    //we ignore repeat packets that were sent because the socket timed out before the packets arrived
                }

                this.socket.setSoTimeout(NEXT_PACKET_TIMEOUT);
                this.packetsReceived++;
            }
            catch (SocketTimeoutException e) {
                //send a message containing the number of packets to be resent
                int packetRequestCount = packetsPending.size();
                sendMsg(ByteConverter.intToByteArray(packetRequestCount));

                //send out a message containing a list of all requested packets to be resent
                sendMsg(ByteConverter.intListToByteArray(packetsPending));
                this.socket.setSoTimeout(0);
            }
        }

        //send a message indicating that no more packets need to be resent
        int packageRequestCount = 0;
        try {
            this.socket.setSoTimeout(NEXT_PACKET_TIMEOUT * 10);
            sendMsg(ByteConverter.intToByteArray(packageRequestCount));
            byte[] endOfTransmissionBuffer = ByteConverter.allocateIntByteArray();
            int msg;
            do {
                readMsg(endOfTransmissionBuffer);
                msg =  ByteConverter.byteArrayToInt(endOfTransmissionBuffer);
            } while (msg != END_OF_TRANSMISSION);
        } catch (SocketTimeoutException e) {

        } catch (IOException e) {
            System.out.println("Failed to end message transmission successfully");
        }

        this.socket.setSoTimeout(0);
    }

    private int getPacketNumber(byte[] msg) {
        byte[] packetNumberAsBytes = Arrays.copyOfRange(msg, 0, PACKET_NUMBER_SIZE);
        int packetNumber = ByteConverter.byteArrayToInt(packetNumberAsBytes);;
        return packetNumber;
    }

    private byte[] getMsgData(byte[] msg, int payloadSize) {
        return Arrays.copyOfRange(msg, PACKET_NUMBER_SIZE, payloadSize);
    }

    @Override
    public boolean outputConnectionIsActive() {
        if (socket == null || remoteAddress == null) {
            return false;
        }
        return (!socket.isClosed());
    }

    @Override
    public boolean inputConnectionIsActive() {
        if (socket == null || remoteAddress == null) {
            return false;
        }
        return (!socket.isClosed());
    }

    @Override
    public void startServer() throws IOException {
        System.out.println("Server running on Port " + PORT);
        try {
            connectToClient();
        } catch (Exception e) {
            System.out.println("Unable to connect to client");
            throw new IOException();
        }

        try {
            while (reply());
        } catch (Exception e) {
            System.out.println("A Server Exception occurred");
            disconnectFromRemoteHost();
            throw new IOException();
        }
        disconnectFromRemoteHost();
    }

    @Override
    public void startServer(int maxNumberOfRequests) throws IOException {
        System.out.println("Server running on Port " + PORT);
        try {
            connectToClient();
        } catch (Exception e) {
            System.out.println("Unable to connect to client");
            throw new IOException();
        }

        System.out.println("Connected to client");
        try {
            for (int i = 0; i < maxNumberOfRequests; i++) {
               while(reply());
            }
        } catch (Exception e) {
            System.out.println("A Server Exception occurred");
            e.printStackTrace();
            throw new IOException();
        }
        disconnectFromRemoteHost();
    }

    @Override
    public String getProtocolString() {
        return "UDP";
    }

}