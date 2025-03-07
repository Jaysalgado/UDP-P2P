package p2p;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;

import com.google.gson.Gson;


public class WsUdpReceiver {

    private static final int PORT = 9999;
    private Peer peer;
    private Gson gson = new Gson();

    public WsUdpReceiver(Peer peer) {
        this.peer = peer;

    }

    public void start() {
        try (DatagramSocket socket = new DatagramSocket(PORT)) {
            byte[] buffer = new byte[1024];

//            System.out.println("WebSocket UDP Receiver started on port " + PORT);

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
//                System.out.println("Received from WebSocket Proxy: " + message);

                InetAddress senderAddress = packet.getAddress();
                int senderPort = packet.getPort();

                processMessage(message, senderAddress, senderPort, socket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private  void processMessage(String message, InetAddress address, int port, DatagramSocket socket) {
        HashMap<Object, Object> response = new HashMap<>();
        if (message.equals("GET_PEERS")) {
//            System.out.println("Received request for peers list.");
            response.put("type", "GET_PEERS");
            HashMap<Integer, String> peers = peer.getPeers();
            response.put("data", peers);
            String jsonResponse = gson.toJson(response);
//            System.out.println("peers status " + peers);

            try {
                byte[] responseData = jsonResponse.getBytes();
                DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, address, port);
                socket.send(responsePacket);
                System.out.println("Sent JSON response to WebSocket Proxy: " + jsonResponse);
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else if (message.equals("GET_FILES")){
//            System.out.println("Received request for files list.");
            response.put("type", "GET_FILES");
            List<String> files = peer.listFiles();
            response.put("data", files);
            String jsonResponse = gson.toJson(response);

            try {
                byte[] responseData = jsonResponse.getBytes();
                DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, address, port);
                socket.send(responsePacket);
                System.out.println("Sent JSON response to WebSocket Proxy: " + jsonResponse);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
                else {
            System.out.println("Unknown command: " + message);
        }
    }


}
