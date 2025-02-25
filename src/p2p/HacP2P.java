package p2p;

import java.net.DatagramSocket;
import java.net.*;
import java.security.SecureRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;

public class HacP2P {

    private DatagramSocket sendSocket;
    private DatagramSocket receiveSocket;
    private final int port;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private final List<String> peerIPs = List.of("10.211.55.3", "10.111.150.70", "10.211.55.2"); // Add peer IPs here
    String myIP = "10.211.55.2";

    public HacP2P (int port){
        this.port = port;
        try {
            this.receiveSocket = new DatagramSocket(port);
            this.sendSocket = new DatagramSocket();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void activateHac () {
        new Thread (this::startHeartbeats).start();
        new Thread(this::routeMessages).start();
    }

    private void startHeartbeats ()  {

        String message = "I am well";
        byte[] data = message.getBytes();

        for (String peerIP : peerIPs) {
            if( peerIP.equals(myIP)) {
                continue;
            }
            int interval = secureRandom.nextInt(31) + 1;      // Random interval (1-31 sec)
            System.out.println("Sending heartbeats to " + peerIP + " every " + interval + " seconds");
            scheduler.scheduleAtFixedRate(() -> sendHeartbeats(peerIP, data), 0, interval, TimeUnit.SECONDS);
        }

    }

    private void sendHeartbeats (String peerIP, byte[] data) {

            try {
                HacPacket protocol = new HacPacket(HacPacket.TYPE_HEARTBEAT, (short) peerIPs.indexOf(myIP), System.currentTimeMillis(), data );
                byte[]  packet = protocol.convertToBytes();
                InetAddress address = InetAddress.getByName(peerIP);
                DatagramPacket sendPacket = new DatagramPacket(packet, packet.length, address, port);
                sendSocket.send(sendPacket);
            } catch (Exception e) {
                e.printStackTrace();
            }

    }

    private void routeMessages () {

        byte[] incomingData = new byte[1024];

        while (true) {

            try {
                DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
                receiveSocket.receive(incomingPacket);
                InetAddress ipOut = incomingPacket.getAddress();
                HacPacket packet = HacPacket.convertFromBytes(incomingPacket.getData());
                System.out.println("Received packet from node: " + packet.getNodeID());
                System.out.println("Containing data: " + new String(packet.getData()));
                int port = incomingPacket.getPort();
                System.out.println("Senders IP:" + peerIPs.get(packet.getNodeID()));
                System.out.println("Senders port:" + port);

                if (packet.getType() == HacPacket.TYPE_HEARTBEAT) {
                   checkHeartbeats();
                } else if (packet.getType() == HacPacket.TYPE_FILELIST) {
                    System.out.println("Received file list");
                } else if (packet.getType() == HacPacket.TYPE_FILEUPDATE) {
                    System.out.println("Received file update");
                } else if (packet.getType() == HacPacket.TYPE_FILEDELETE) {
                    System.out.println("Received file delete");
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }


    }
    private void checkHeartbeats () {

    }


}
