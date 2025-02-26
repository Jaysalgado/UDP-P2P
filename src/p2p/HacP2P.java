package p2p;

import java.net.*;
import java.net.DatagramSocket;
import java.security.SecureRandom;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import com.google.gson.Gson;


public class HacP2P {
    private DatagramSocket sendSocket;
    private DatagramSocket receiveSocket;
    private final int port;
    private final String pathToNodeHomeDir = System.getProperty("user.dir") + "/p2p_home";
    private List<Config.Node> peers;
    private final String myIP;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    // private final List<String> peerIPs = List.of("10.211.55.3", "10.111.150.70", "10.211.55.2"); // Add peer IPs here
    // String myIP = "10.211.55.2";

    public HacP2P (int port, List<Config.Node> peers, String myIP){
        this.port = port;
        this.myIP = myIP;
        this.peers = peers != null ? peers : new ArrayList<>();
        try {
            this.receiveSocket = new DatagramSocket(port);
            this.sendSocket = new DatagramSocket();
        } catch (Exception e) {
            e.printStackTrace();
        }
        initHomeDirectory();
    }

    public void activateHac () {
        new Thread (this::startHeartbeats).start();
        new Thread(this::routeMessages).start();
    }

    private void startHeartbeats ()  {
        String message = "I am well";
        byte[] data = message.getBytes();

        for (Config.Node peer : peers) {
            String peerIP = peer.getIp();
            if (peerIP.equals(myIP)) {
                continue;
            }

            int interval = secureRandom.nextInt(31) + 1;      // Random interval (1-31 sec)
            System.out.println("Sending heartbeats to " + peerIP + " every " + interval + " seconds");
            scheduler.scheduleAtFixedRate(() -> sendHeartbeats(peerIP, data), 0, interval, TimeUnit.SECONDS);
        }

    }

    private void sendHeartbeats (String peerIP, byte[] data) {
        short nodeID = -1;

        // Find the index of myIP in config.json
        for (int i = 0; i < peers.size(); i++) {
            if (peers.get(i).getIp().equals(myIP)) {
                nodeID = (short) i;
                break;
            }
        }

        // If not found in our config.json
        if (nodeID == -1) {
            System.out.println("Warning: Could not find myIP in peer list.");
            return;
        }

        try {
            HacPacket protocol = new HacPacket(HacPacket.TYPE_HEARTBEAT, nodeID, System.currentTimeMillis(), data);
            byte[] packet = protocol.convertToBytes();
            InetAddress address = InetAddress.getByName(peerIP);
            DatagramPacket sendPacket = new DatagramPacket(packet, packet.length, address, port);
            sendSocket.send(sendPacket);
            System.out.println("Sent heartbeat to: " + peerIP);
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
                InetAddress senderIP = incomingPacket.getAddress();
                HacPacket packet = HacPacket.convertFromBytes(incomingPacket.getData());

                System.out.println("Received packet from node ID: " + packet.getNodeID());
                System.out.println("Containing data: " + new String(packet.getData()));

                int senderPort = incomingPacket.getPort();

                // Validate node ID before accessing peers list
                if (packet.getNodeID() >= 0 && packet.getNodeID() < peers.size()) {
                    System.out.println("Sender's IP: " + peers.get(packet.getNodeID()).getIp());
                    System.out.println("Sender's Port: " + senderPort);
                } else {
                    System.out.println("Unknown sender ID: " + packet.getNodeID());
                }

                // Handle different packet types
                switch (packet.getType()) {
                    case HacPacket.TYPE_HEARTBEAT:
                        checkHeartbeats(packet);
                        break;
                    case HacPacket.TYPE_FILELIST:
                        System.out.println("Received file list");
                        break;
                    case HacPacket.TYPE_FILEUPDATE:
                        System.out.println("Received file update");
                        break;
                    case HacPacket.TYPE_FILEDELETE:
                        System.out.println("Received file delete");
                        break;
                    default:
                        System.out.println("Received unknown packet type.");
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void checkHeartbeats (HacPacket packet) {
        if (packet.getType() == HacPacket.TYPE_HEARTBEAT) {
            System.out.println("Received heartbeat from node: " + packet.getNodeID());
        }
    }

    // Initializes home directory if it doesn't exist
    private void initHomeDirectory() {
        File file = new File(pathToNodeHomeDir);
        if (!file.exists()) {
            file.mkdirs();
            System.out.println("Home directory not detected. Created: " + pathToNodeHomeDir);
        }
    }

    // Retrieves the list of items in the directory
    public List<String> retrieveDirItems() {
        File dir = new File(pathToNodeHomeDir);
        if (!dir.exists() || !dir.isDirectory()) {
            System.out.println("Home directory does not exist.");
            return new ArrayList<>();
        }

        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            System.out.println("No files present in home directory.");
            return new ArrayList<>();
        }

        List<String> fileNames = new ArrayList<>();
        for (File file : files) {
            if (file.isFile()) {
                fileNames.add(file.getName());
            }
        }
        return fileNames;
    }

    // Sends the list of items in the home directory to all peers
    public void sendHomeDir() {
        List<String> allFileNames = retrieveDirItems();

        if (allFileNames.isEmpty()) {
            System.out.println("There is nothing in the home directory.");
            return;
        }

        if (peers.isEmpty()) {
            System.out.println("No peers found in config.json.");
            return;
        }

        for (Config.Node node : peers) {
            sendFileList(node.getIp(), allFileNames);
            System.out.println("Sent file list to " + node.getIp() + ":" + node.getPort());

            for (String fileName : allFileNames) {
                File file = new File(pathToNodeHomeDir, fileName);
                sendFile(node.getIp(), file);
            }
        }
    }

    // Adds a file to the home directory
    public void addFile(File file) {
        File destFile = new File(pathToNodeHomeDir, file.getName());

        try {
            if (!file.exists()) {
                System.out.println("File does not exist: " + file.getAbsolutePath());
                return;
            }

            Files.copy(file.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("File added: " + destFile.getAbsolutePath());

            sendHomeDir();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Deletes a file from the home directory
    public void deleteFile(String fileName) {
        File file = new File(pathToNodeHomeDir, fileName);

        if (!file.exists()) {
            System.out.println("File does not exist: " + fileName);
            return;
        }

        if (file.delete()) {
            System.out.println("File deleted: " + fileName);
            notifyPeersFileDeleted(fileName);
        } else {
            System.out.println("Failed to delete file: " + fileName);
        }
    }

    // Notifies peers that a file was deleted
    private void notifyPeersFileDeleted(String fileName) {
        if (peers.isEmpty()) {
            System.out.println("No peers found in config.json.");
            return;
        }

        for (Config.Node node : peers) {
            sendFileDeletion(node.getIp(), fileName);
            System.out.println("Notified " + node.getIp() + " about deleted file: " + fileName);
        }
    }

    // Sends files
    public void sendFile(String peerIP, File file) {
        try {
            byte[] fileData = Files.readAllBytes(file.toPath()); // Read file content
            InetAddress address = InetAddress.getByName(peerIP);
            DatagramPacket packet = new DatagramPacket(fileData, fileData.length, address, port);
            sendSocket.send(packet);

            System.out.println("Sent file: " + file.getName() + " to " + peerIP);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendFileDeletion(String peerIP, String fileName) {
        try {
            String message = "DELETE:" + fileName;
            byte[] data = message.getBytes();
            InetAddress address = InetAddress.getByName(peerIP);
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            sendSocket.send(packet);

            System.out.println("Notified " + peerIP + " about deleted file: " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendFileList(String peerIP, List<String> fileList) {
        try {
            String fileListJson = new Gson().toJson(fileList);  // Convert file list to JSON
            byte[] data = fileListJson.getBytes();

            InetAddress address = InetAddress.getByName(peerIP);
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            sendSocket.send(packet);  // Send the JSON metadata

            System.out.println("Sent file list to: " + peerIP);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
