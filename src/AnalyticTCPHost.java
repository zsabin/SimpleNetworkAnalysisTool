import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

public class AnalyticTCPHost extends AnalyticHost {

    ServerSocket serverSocket;
    Socket clientSocket;
    String remoteHost;
    OutputStream out;
    InputStream in;

    public AnalyticTCPHost(){
        this.serverSocket = null;
        this.clientSocket = null;
        this.out = null;
        this.in = null;
        this.rand = new Random();
    }

    public void connectToRemoteHost(String remoteHost) throws IOException {
        this.remoteHost = remoteHost;
        this.clientSocket = new Socket(remoteHost, PORT);
        this.out = this.clientSocket.getOutputStream();
        this.in = this.clientSocket.getInputStream();
    }

    @Override
    public void disconnectFromRemoteHost() throws IOException {
        byte[] header = buildHeader(CLOSE_CONNECTION, 0, 0);
        try {
            sendMsg(header);
        } catch (IOException e) {
            System.out.println("Header Failed to Send");
        }

        this.out.close();
        this.out = null;

        this.in.close();
        this.in = null;

        this.remoteHost = null;

        this.clientSocket.close();
        this.clientSocket = null;
    }

    @Override
    public boolean outputConnectionIsActive() {
        if(out == null){
            return false;
        }
        if (serverSocket != null){
            return (!serverSocket.isClosed());
        }
        else if (clientSocket != null) {
            return (!clientSocket.isClosed() );
        }
        return false;
    }

    @Override
    public boolean inputConnectionIsActive() {
        if (in == null){
            return false;
        }
        if (serverSocket != null){
            return (!serverSocket.isClosed());
        }
        else if (clientSocket != null) {
            return (!clientSocket.isClosed());
        }
        return false;
    }

    @Override
    public void sendByte(int data) throws IOException {
        if(!outputConnectionIsActive()) {
            System.out.println("Output Connection is inactive");
            throw new IOException();
        }
        out.write(data);
    }

    @Override
    public void sendMsg(byte[] data) throws IOException {
        if(!outputConnectionIsActive()) {
            System.out.println("Output Connection is inactive");
            throw new IOException();
        }
        out.write(data);
    }

    @Override
    public int readByte() throws IOException {
        if(!inputConnectionIsActive()) {
            System.out.println("Input Connection is inactive");
            throw new IOException();
        }
       return in.read();
    }

    @Override
    public void readMsg(byte[] bytes) throws IOException {
        if(!inputConnectionIsActive()) {
            System.out.println("Input Connection is inactive");
            throw new IOException();
        }

        int totalBytesReadIn = 0;
        do {
            int bytesReadIn = in.read(bytes);
            if (bytesReadIn == -1) {
                System.out.println("Error reading Message");
                throw new IOException();
            }
            totalBytesReadIn += bytesReadIn;
        }
        while(totalBytesReadIn < bytes.length);
    }

    @Override
    public void startServer() throws IOException {
        this.serverSocket = new ServerSocket(PORT);

        System.out.println("Server running on port " + PORT);
        while (listen(serverSocket));
        serverSocket.close();
        this.serverSocket = null;
    }

    @Override
    public void startServer(int maxNumberOfRequests) throws IOException {
        this.serverSocket = new ServerSocket(PORT);

        System.out.println("Server running on port " + PORT);
        for (int i = 0; i < maxNumberOfRequests; i++){
            if (!listen(serverSocket)) {
                break;
            }
        }
        serverSocket.close();
        this.serverSocket = null;
    }

    private boolean listen(ServerSocket serverSocket) {
        try {
            Socket client = serverSocket.accept();
            this.out = client.getOutputStream();
            this.in = client.getInputStream();

            while (reply());
        } catch(IOException e) {
            System.out.println("Server IO Exception occurred");
            return false;
        } catch (Exception e) {
            System.out.println("A Server Exception occurred");
            return false;
        }
        return true;
    }

    /* Measures the time it takes to fully transfer a message of a given size using smaller fixed-size packets */
    public long packetTransferTest(int totalByteCount, int msgSize) throws IOException {
        if (!isConnectedToRemoteHost()) {
            System.out.println("No Connection Established with Remote Host");
            throw new IOException();
        }

        long startTime, endTime;

        //send header
        byte[] header = buildHeader(ACK_REQUEST, totalByteCount, msgSize);
        try {
            sendMsg(header);
        } catch(IOException e) {
            System.out.println("An Error occurred sending header");
            throw new IOException();
        }

        //build message
        byte[] msg = new byte[msgSize];
        rand.nextBytes(msg);

        //start time and begin sending messages
        startTime = System.nanoTime();
        for (int bytesSent = 0; bytesSent < totalByteCount; bytesSent += msgSize)
        {
            try {
                sendMsg(msg);
            } catch (IOException e) {
                System.out.println("An Error occurred in sending the message");
                throw new IOException();
            }

            try {
                readACK();
            } catch (IOException e) {
                System.out.println("An Error occurred in receiving ACK");
                throw new IOException();
            }
        }
        endTime = System.nanoTime();

        return endTime - startTime;
    }

    @Override
    public String getProtocolString() {
        return "TCP";
    }
}
