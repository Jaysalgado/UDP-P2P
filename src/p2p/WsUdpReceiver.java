package p2p;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import com.google.gson.Gson;


public class WsUdpReceiver {

    private static final int PORT = 9999;
    private Peer peer; // Reference to Peer instance
    private Gson gson = new Gson(); // Gson instance for JSON conversion

    public WsUdpReceiver(Peer peer) {
        this.peer = peer; // Store reference to the Peer instance
    }

    public void start() {
        try (DatagramSocket socket = new DatagramSocket(PORT)) {
            byte[] buffer = new byte[1024];

            System.out.println("WebSocket UDP Receiver started on port " + PORT);

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                System.out.println("Received from WebSocket Proxy: " + message);

                // Get sender details
                InetAddress senderAddress = packet.getAddress();
                int senderPort = packet.getPort();

                // Process the message and send response
                processMessage(message, senderAddress, senderPort, socket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private  void processMessage(String message, InetAddress address, int port, DatagramSocket socket) {
        // Example: Add logic to integrate with your HAC system
        if (message.equals("GET_PEERS")) {
            System.out.println("Received request for file list.");

            HashMap<Integer, String> peers = peer.getPeers(); // Get file list from Peer
            String jsonResponse = gson.toJson(peers); // Convert HashMap to JSON
            System.out.println("peers status " + peers);

            try {
                byte[] responseData = jsonResponse.getBytes();
                DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, address, port);
                socket.send(responsePacket); // Send JSON response
                System.out.println("Sent JSON response to WebSocket Proxy: " + jsonResponse);
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            System.out.println("Unknown command: " + message);
        }
    }


}
