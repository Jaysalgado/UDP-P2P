package p2p;

import java.net.DatagramSocket;
import java.net.*;
import java.security.SecureRandom;
import java.util.List;
import com.google.gson.Gson;

public class HacP2P {

    private DatagramSocket socket;
    private int port;

    public HacP2P (int port){
        this.port = port;
        try {
            socket = new DatagramSocket(port);
//            socket.setReuseAddress(true);
//            socket.bind(new InetSocketAddress(port));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void activateHac () {
        new Thread(this::sendHeartbeats).start();
        new Thread(this::checkHeartbeats).start();
    }

    private void sendHeartbeats () {

        String message = "I am well";
        byte[] data = message.getBytes();
        SecureRandom secureRandom = new SecureRandom();

        while (true) {
            try {
                HacPacket protocol = new HacPacket(HacPacket.TYPE_HEARTBEAT, (short) 0, System.currentTimeMillis(), data );
                byte[]  packet = protocol.convertToBytes();
                Thread.sleep(secureRandom.nextInt(31) * 1000);
                InetAddress address = InetAddress.getByName("localhost");
                DatagramPacket sendPacket = new DatagramPacket(packet, packet.length, address, port);
                socket.send(sendPacket);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    private void checkHeartbeats () {
        byte[] incomingData = new byte[1024];

        while (true) {

            try {
                DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
                socket.receive(incomingPacket);
                HacPacket packet = HacPacket.convertFromBytes(incomingPacket.getData());
                InetAddress IPAddress = incomingPacket.getAddress();
                int port = incomingPacket.getPort();

                if (packet.getType() == HacPacket.TYPE_FILELIST) {
                    String fileListJson = new String(packet.getData());
                    List<String> receivedFileList = new Gson().fromJson(fileListJson, List.class);

                    System.out.println("Received file list from node " + packet.getNodeID() + ":");
                    for (String fileName : receivedFileList) {
                        System.out.println(" - " + fileName);
                    }
                } else {
                    System.out.println("Received packet from node: " + packet.getNodeID());
                    System.out.println("Containing data: " + new String(packet.getData()));
                }

                System.out.println("Client IP:" + IPAddress.getHostAddress());
                System.out.println("Client port:" + port);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void sendFileList(String ipAddress, List<String> fileList) {
        if (socket == null) {
            System.out.println("Error: Socket is not initialized.");
            return;
        }
        try {
            String jsonFileList = new Gson().toJson(fileList);
            byte[] data = jsonFileList.getBytes();

            HacPacket protocol = new HacPacket(HacPacket.TYPE_FILELIST, (short) 0, System.currentTimeMillis(), data);
            byte[] packet = protocol.convertToBytes();

            InetAddress address = InetAddress.getByName(ipAddress);
            DatagramPacket sendPacket = new DatagramPacket(packet, packet.length, address, port);
            socket.send((sendPacket));

            System.out.println("Sent file list to " + ipAddress);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
