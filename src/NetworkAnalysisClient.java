import java.io.*;
import java.net.InetAddress;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/* A simple class used to measaure latency and throughput metrics between hosts */
public class NetworkAnalysisClient {

    public static final int kilobyte = (int)(Math.pow(2, 10));
    public static final int megabyte = (int)(Math.pow(2, 20));

    public static void main(String[] args) throws IOException {
        String host;
        String resultsFileName;
        int udpTimeout;

        if (args.length < 2 ){
            System.out.println("Invalid command-line arguments");
            return;
        }

        host = args[0];

        resultsFileName = args[1];
        PrintWriter resultsWriter = null;
        try {
            resultsWriter = new PrintWriter(resultsFileName);
        } catch (FileNotFoundException e) {
            System.out.println("Results File not found");
            return;
        }

        AnalyticTCPHost tcpClient = new AnalyticTCPHost();
        AnalyticUDPHost udpClient = new AnalyticUDPHost();

        long startTime = System.currentTimeMillis();
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date date = new Date();

        resultsWriter.println("NETWORK ANALYSIS TEST RESULTS");
        resultsWriter.println("START TIMESTAMP: " + dateFormat.format(date));
        resultsWriter.println("LOCAL HOST: " + InetAddress.getLocalHost().getHostName());
        resultsWriter.println("REMOTE HOST: " + host);
        resultsWriter.println("PORT: " + Host.PORT);
        resultsWriter.println();

        try {
            udpClient.connectToServer(host);
            resultsWriter.println("Running UDP Tests With Timeout Value: " + udpClient.NEXT_PACKET_TIMEOUT);
            runRoundTripLatencyTestSuite(udpClient, 100, resultsWriter);
            runThroughputTestSuite(udpClient, 50, resultsWriter);
        } catch(Exception e) {
            resultsWriter.println("An Error Occurred: UDP Tests Failed to Complete");
            System.out.println("An Error Occurred: UDP Tests Failed to Complete");
        }
        udpClient.disconnectFromRemoteHost();

        try {
            tcpClient.connectToRemoteHost(host);
            runRoundTripLatencyTestSuite(tcpClient, 100, resultsWriter);
            runThroughputTestSuite(tcpClient, 50, resultsWriter);
            runPacketTransferTestSuite(tcpClient, 50, resultsWriter);
        } catch(Exception e) {
            resultsWriter.println("An Error Occurred: TCP Tests Failed to Complete");
            System.out.println("An Error Occurred: TCP Tests Failed to Complete");
        }
        tcpClient.disconnectFromRemoteHost();

        resultsWriter.println();
        date = new Date();
        resultsWriter.println("END TIMESTAMP: " + dateFormat.format(date));
        resultsWriter.close();

        long endTime = System.currentTimeMillis();
        System.out.println("Test Finished in " + (endTime - startTime) + " ms");
    }

    /* Measures round-trip latency time with the remote host using messages of 1 byte, 32 bytes of 1kb. */
    private static void runRoundTripLatencyTestSuite(AnalyticHost client, int numOfCases, PrintWriter resultsWriter) throws IOException {
        int class1MsgSize = 1;
        int class2MsgSize= 32;
        int class3MsgSize = kilobyte;

        System.out.println("Running " + client.getProtocolString() + " Round Trip Latency Test Suite...");

        resultsWriter.println(client.getProtocolString() + " Round Trip Latency Test");
        resultsWriter.print("Test Case,");
        resultsWriter.print(class1MsgSize + ",");
        resultsWriter.print(class2MsgSize + ",");
        resultsWriter.print(class3MsgSize);
        resultsWriter.println();

        for (int i = 0; i < numOfCases; i++) {
            try {
                resultsWriter.print((i + 1) + ",");
                resultsWriter.print(client.echoTest(class1MsgSize) + ",");
                resultsWriter.print(client.echoTest(class2MsgSize) + ",");
                resultsWriter.print(client.echoTest(class3MsgSize));
                resultsWriter.println();
            }	catch (Exception e) {
                System.out.println("Round Trip Latency Test " + i + " Failed");
                e.printStackTrace();
                throw new IOException();
            }
        }

        resultsWriter.println();
        System.out.println("Round Trip Latency Test Suite Completed Successfully");
    }

    /* Measures round-trip latency time with the remote host in both directions using messages of 1, 16, 64, and 256kb.
     * Outputs the results in both directions. These metrics will then be used to estimate throughput.
     */
    private static void runThroughputTestSuite(AnalyticHost client, int numOfCases, PrintWriter resultsWriter) throws IOException{
        ArrayList<Integer> testClasses = new ArrayList<Integer>();
        testClasses.add(kilobyte);
        testClasses.add(16 * kilobyte);
        testClasses.add(64 * kilobyte);
        testClasses.add(256 * kilobyte);
        testClasses.add(megabyte);

        System.out.println("Running " + client.getProtocolString() + " Throughput Test Suite...");

        resultsWriter.println(client.getProtocolString() + " Throughput Test");
        resultsWriter.print(",");
        for (Integer testClass : testClasses){
            resultsWriter.print(testClass + "," + testClass + ',');
        }
        resultsWriter.println();

        resultsWriter.print("Test Case,");
        for (int i = 0; i < testClasses.size(); i++){
            resultsWriter.print("Client to Server, Server to Client,");
        }
        resultsWriter.println();

        for (int i = 0; i < numOfCases; i++) {
            try {
                resultsWriter.print((i + 1) + ",");
                for (Integer testClass : testClasses) {
                    ArrayList<Long> results = client.throughputTest(testClass);
                    resultsWriter.print(results.get(0) + ",");
                    resultsWriter.print(results.get(1) + ",");
                }
                resultsWriter.println();
            } catch (Exception e) {
                System.out.println("Throughput Test " + i + " Failed");
                throw new IOException();
            }
        }

        resultsWriter.println();
        System.out.println("Throughput Test Suite Completed Successfully");
    }

    /* Measures the time it takes to fully transfer a 1MB message using TCP with fixed-size packets of 512 bytes, 1kb, 2kb, or 4kb */
    private static void runPacketTransferTestSuite(AnalyticTCPHost client, int numOfCases, PrintWriter resultsWriter) throws IOException {
        int totalMsgSize = megabyte;
        ArrayList<Integer> packetSizeClasses = new ArrayList<Integer>();
        packetSizeClasses.add(4 * kilobyte);
        packetSizeClasses.add(2 * kilobyte);
        packetSizeClasses.add(kilobyte);
        packetSizeClasses.add(512);

        System.out.println("Running Package Transfer Test...");

        resultsWriter.println(client.getProtocolString() + " Package Transfer Test");
        resultsWriter.print(",");

        for (Integer packetSize : packetSizeClasses) {
            resultsWriter.print(packetSize + ",");
        }
        resultsWriter.println();

        for (int i = 0; i < numOfCases; i++) {
            try {
                resultsWriter.print((i + 1) + ",");
                for (Integer packetSize : packetSizeClasses) {
                    long value = client.packetTransferTest(totalMsgSize, packetSize);
                    resultsWriter.print( value + ",");
                }
                resultsWriter.println();
            } catch (IOException e) {
                System.out.println("Package Transfer Test " + i + " Failed");
                throw new IOException();
            }
        }

        resultsWriter.println();
        System.out.println("Packet Transfer Test Suite Completed Successfully");
    }
}