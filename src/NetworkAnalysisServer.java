import java.io.IOException;

public class NetworkAnalysisServer {

    public static void main(String[] args) {
        int maxNumberOfRequests = 1;

        AnalyticHost udpServer = new AnalyticUDPHost();
        AnalyticHost tcpServer = new AnalyticTCPHost();

        try {
            udpServer.startServer(maxNumberOfRequests);
        } catch (IOException e){
            System.out.println("An IOException occurred on the udp server");
        }

       try {
            tcpServer.startServer(maxNumberOfRequests);
        } catch (IOException e){
            System.out.println("An IOException occurred on the tcp server");
        }
    }
}
