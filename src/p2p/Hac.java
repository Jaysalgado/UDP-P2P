package p2p;

import java.net.DatagramSocket;
import java.net.*;
import java.io.*;
import java.security.SecureRandom;

public class Hac {

    private DatagramSocket socket;
    private int port;


    public Hac (int port){
        this.port = port;
        try {
            socket = new DatagramSocket(port);
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
                Protocol protocol = new Protocol(Protocol.TYPE_HEARTBEAT, (short) 0, System.currentTimeMillis(), data );
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
                Protocol packet = Protocol.convertFromBytes(incomingPacket.getData());
                System.out.println("Received packet from node: " + packet.getNodeID());
                System.out.println("Containing data: " + new String(packet.getData()));
                int port = incomingPacket.getPort();

//                System.out.println("Received message from client: " + message);
//                System.out.println("Client IP:" + IPAddress.getHostAddress());
//                System.out.println("Client port:" + port);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


}
