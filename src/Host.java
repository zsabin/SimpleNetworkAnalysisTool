import java.io.IOException;
import java.util.Random;

public abstract class Host {

    static final int PORT = 2691;
    static final int ACK = 6;

    Random rand;

    public abstract void sendByte(int data) throws IOException;

    public void sendInteger(int data) throws IOException {
        sendMsg(ByteConverter.intToByteArray(data));
    }

    public void sendLong(long data) throws IOException {
        sendMsg(ByteConverter.longToByteArray(data));
    }

    public abstract void sendMsg(byte[] msg) throws IOException;

    public abstract int readByte() throws IOException;

    public void readACK() throws IOException {
        if(readByte() != ACK){
            throw new IOException();
        }
    }

    public int readInteger() throws IOException {
        byte[] intAsBytes = ByteConverter.allocateIntByteArray();
        readMsg(intAsBytes);
        return ByteConverter.byteArrayToInt(intAsBytes);
    }

    public long readLong() throws IOException {
        byte[] longAsBytes = ByteConverter.allocateLongByteArray();
        readMsg(longAsBytes);
        return ByteConverter.byteArrayToLong(longAsBytes);
    }

    public abstract void readMsg(byte[] bytes) throws IOException;

    public abstract void disconnectFromRemoteHost() throws IOException;

    public Boolean isConnectedToRemoteHost() {
        return outputConnectionIsActive() && inputConnectionIsActive();
    }

    public abstract boolean outputConnectionIsActive();

    public abstract boolean inputConnectionIsActive();

    public abstract void startServer() throws IOException;

    public abstract void startServer(int maxNumberOfRequests) throws IOException;

    public abstract String getProtocolString();
}
