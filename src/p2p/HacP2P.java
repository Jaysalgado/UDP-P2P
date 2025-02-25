package p2p;

import java.io.File;
import java.nio.file.Files;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.*;
import java.security.SecureRandom;
import java.util.List;
import com.google.gson.Gson;

public class HacP2P {

    private DatagramSocket socket;
    private int port;

    public HacP2P(int port) {
        this.port = port;
        try {
            socket = new DatagramSocket(port);
//            socket.setReuseAddress(true);
//            socket.bind(new InetSocketAddress(port));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void activateHac() {
        new Thread(this::sendHeartbeats).start();
        new Thread(this::checkHeartbeats).start();
    }

    private void sendHeartbeats() {

        String message = "I am well";
        byte[] data = message.getBytes();
        SecureRandom secureRandom = new SecureRandom();

        while (true) {
            try {
                HacPacket protocol = new HacPacket(HacPacket.TYPE_HEARTBEAT, (short) 0, System.currentTimeMillis(), data);
                byte[] packet = protocol.convertToBytes();
                Thread.sleep(secureRandom.nextInt(31) * 1000);
                InetAddress address = InetAddress.getByName("localhost");
                DatagramPacket sendPacket = new DatagramPacket(packet, packet.length, address, port);
                socket.send(sendPacket);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    private void checkHeartbeats() {
        byte[] incomingData = new byte[1024];

        while (true) {
            try {
                DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
                socket.receive(incomingPacket);
                HacPacket packet = HacPacket.convertFromBytes(incomingPacket.getData());
                InetAddress senderIP = incomingPacket.getAddress();
                int senderPort = incomingPacket.getPort();

                if (packet.getType() == HacPacket.TYPE_FILELIST) {
                    String fileListJson = new String(packet.getData());
                    List<String> receivedFileList = new Gson().fromJson(fileListJson, List.class);

                    System.out.println("Received file list from node " + packet.getNodeID() + ":");
                    for (String fileName : receivedFileList) {
                        System.out.println(" - " + fileName);
                    }
                } else if (packet.getType() == HacPacket.TYPE_FILE_TRANSFER) {
                    File receivedFile = new File("p2p_home/" + System.currentTimeMillis() + "_received");
                    Files.write(receivedFile.toPath(), packet.getData());
                    System.out.println("Received file: " + receivedFile.getAbsolutePath());
                } else if (packet.getType() == HacPacket.TYPE_FILEDELETE) {
                    String fileName = new String(packet.getData());
                    File fileToDelete = new File("p2p_home/" + fileName);

                    if (fileToDelete.exists() && fileToDelete.delete()) {
                        System.out.println("Deleted file received from peer: " + fileName);
                    } else {
                        System.out.println("File deletion request received, but file not found: " + fileName);
                    }
                }

                System.out.println("Sender IP: " + senderIP.getHostAddress());
                System.out.println("Sender port: " + senderPort);
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

    public void sendFile(String ipAddress, File file) {
        try {
            byte[] fileData = Files.readAllBytes(file.toPath());

            HacPacket protocol = new HacPacket(HacPacket.TYPE_FILE_TRANSFER, (short) 0, System.currentTimeMillis(), fileData);
            byte[] packet = protocol.convertToBytes();

            InetAddress address = InetAddress.getByName(ipAddress);
            DatagramPacket sendPacket = new DatagramPacket(packet, packet.length, address, port);
            socket.send(sendPacket);

            System.out.println("Sent file: " + file.getName() + " to " + ipAddress);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendFileDeletion(String ipAddress, String fileName) {
        try {
            byte[] data = fileName.getBytes();

            HacPacket packet = new HacPacket(HacPacket.TYPE_FILEDELETE, (short) 0, System.currentTimeMillis(), data);
            byte[] packetData = packet.convertToBytes();

            InetAddress address = InetAddress.getByName(ipAddress);
            DatagramPacket sendPacket = new DatagramPacket(packetData, packetData.length, address, port);
            socket.send(sendPacket);

            System.out.println("Sent file deletion request for " + fileName + " to " + ipAddress);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
