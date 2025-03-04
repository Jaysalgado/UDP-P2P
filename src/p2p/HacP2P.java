package p2p;

import java.net.*;
import java.net.DatagramSocket;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import com.google.gson.Gson;
import java.nio.ByteBuffer;

public class HacP2P {
    private DatagramSocket sendSocket;
    private DatagramSocket receiveSocket;
    private final int port;
    private final String pathToNodeHomeDir = System.getProperty("user.dir") + "/p2p_home";
    private List<Config.Node> peers;
    private final String myIP;
    private final int selfNodeID;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private ExecutorService messageHandlerPool = Executors.newFixedThreadPool(6);
    public HashMap<Integer, String> activePeers = new HashMap<>();
    private HashMap<Integer, Long> lastHeartbeat = new HashMap<>();

    public HacP2P (int port, List<Config.Node> peers, String myIP){
        this.port = port;
        this.myIP = myIP;
        this.peers = peers != null ? peers : new ArrayList<>();

        this.selfNodeID = peers.stream()
                .filter(node -> node.getIp().equals(myIP))
                .map(Config.Node::getId)
                .findFirst()
                .orElse(-1);

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
        new Thread(this::listen).start();
        scheduler.scheduleAtFixedRate(this::isAlive, 10, 30, TimeUnit.SECONDS);
    }

    private void startHeartbeats ()  {

        List<String> allFileNames = retrieveDirItems();
        List<String> filteredFileNames = allFileNames.stream()
                .filter(fileName -> !fileName.equalsIgnoreCase("config.json"))
                .toList();
        String fileListJson = new Gson().toJson(filteredFileNames);
        byte[] data = fileListJson.getBytes();

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

    private void listen () {

        byte[] incomingData = new byte[1024];

        while (true) {
            try {
                DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
                receiveSocket.receive(incomingPacket);

                // Submit task to thread pool for processing
                messageHandlerPool.submit(() -> routeMessages(incomingPacket));

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
    private void routeMessages (DatagramPacket incomingPacket) {

            try {

                InetAddress senderIP = incomingPacket.getAddress();

                if (incomingPacket.getLength() < 16) {
                    System.out.println("Received packet is too small: " + incomingPacket.getLength() + " bytes. Ignoring.");
                    return;
                }

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
                        compareFileLists(packet);
                        break;
                    case HacPacket.TYPE_FILELIST:
                        System.out.println("Received file list");
                        compareFileLists(packet);
                        break;
                    case HacPacket.TYPE_FILEUPDATE:
                        System.out.println("Received file update");
                        break;
                    case HacPacket.TYPE_FILEDELETE:
                        System.out.println("Received file delete");
                        break;
                    case HacPacket.TYPE_FILETRANSFER:
                        System.out.println("Received file transfer request.");
                        String dataString = new String(packet.getData());
                        if (dataString.startsWith("REQUEST:")) {
                            String fileName = dataString.substring(8);
                            sendFile(senderIP.getHostAddress(), new File(pathToNodeHomeDir, fileName));
                        } else {
                            receiveFile(packet);
                        }
                        receiveFile(packet);
                        break;
                    default:
                        System.out.println("Received unknown packet type.");
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
    }

    private void checkHeartbeats (HacPacket packet) {
        System.out.println("Received heartbeat from node: " + packet.getNodeID());
        activePeers.put((int) packet.getNodeID(), "ACTIVE");
        lastHeartbeat.put((int) packet.getNodeID(), System.currentTimeMillis());
    }

    private void isAlive () {
        long currentTime = System.currentTimeMillis();
        for (int nodeID : lastHeartbeat.keySet()) {
            if (currentTime - lastHeartbeat.get(nodeID) > 31000) {
                System.out.println("Node " + nodeID + " is inactive.");
                activePeers.put(nodeID, "INACTIVE");
            }
        }
    }

    // Checks to see if the home directory exists, if not we create it.
    private void initHomeDirectory() {
        File file = new File(pathToNodeHomeDir);
        if (!file.exists()) {
            file.mkdirs();
            System.out.println("Home directory not detected. Created: " + pathToNodeHomeDir);
        }
    }

    // Retrieves the file names in the home directory.
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

    // Allows us to add a file from an input path to anywhere on our PC.
    // Connects to sendFileList() in order to communicate with peers regarding the addition.
    public void addFile(File file) {
        File destFile = new File(pathToNodeHomeDir, file.getName());

        try {
            if (!file.exists()) {
                System.out.println("File does not exist: " + file.getAbsolutePath());
                return;
            }

            Files.copy(file.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("File added: " + destFile.getAbsolutePath());

            sendFileList();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Retrieves the file list for that specific node, converts it to a JSON and sends it to all connected nodes.
    public void sendFileList() {
        List<String> allFileNames = retrieveDirItems();

        if (allFileNames.isEmpty()) {
            System.out.println("There is nothing in the home directory.");
            return;
        }

        if (peers.isEmpty()) {
            System.out.println("No peers found in config.json.");
            return;
        }

        List<String> filteredFileNames = allFileNames.stream()
                .filter(fileName -> !fileName.equalsIgnoreCase("config.json"))
                .toList();

        if (filteredFileNames.isEmpty()) {
            System.out.println("No files aside from config.json present.");
            return;
        }

        try {
            String fileListJson = new Gson().toJson(allFileNames);
            byte[] data = fileListJson.getBytes();

            HacPacket packet = new HacPacket(HacPacket.TYPE_FILELIST, (short) selfNodeID, System.currentTimeMillis(), data);
            byte[] packetBytes = packet.convertToBytes();

            for (Config.Node node : peers) {
                if (node.getIp().equals(myIP)) {
                    continue;
                }
                InetAddress address = InetAddress.getByName(node.getIp());
                DatagramPacket sendPacket = new DatagramPacket(packetBytes, packetBytes.length, address, port);
                sendSocket.send(sendPacket);
                System.out.println("Sent file list to: " + node.getIp());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Allows a node to compare the file list it receives with its own file list and send a request for missing files.
    private void compareFileLists(HacPacket packet) {
        byte[] data = packet.getData();
        if (data.length == 0) {
            System.out.println("Received an empty file list.");
            return;
        }

        String jsonString = new String(data);
        List<String> receivedFileList = new Gson().fromJson(jsonString, List.class);

        List<String> localFiles = retrieveDirItems();

        for (String fileName : receivedFileList) {
            if (!localFiles.contains(fileName)) {
                System.out.println("Missing file detected: " + fileName + " - Requesting from Node " + packet.getNodeID());
                requestFile(packet.getNodeID(), fileName);
            }
        }
    }

    // Method to read a file, use HacPacket to transform it into a message and send it to the node that requires it.
    public void sendFile(String peerIP, File file) {
        try {
            byte[] fileData = Files.readAllBytes(file.toPath());
            byte[] fileNameBytes = file.getName().getBytes();

            ByteBuffer buffer = ByteBuffer.allocate(1 + fileNameBytes.length + fileData.length);
            buffer.put((byte) fileNameBytes.length);
            buffer.put(fileNameBytes);
            buffer.put(fileData);

            HacPacket packet = new HacPacket(HacPacket.TYPE_FILETRANSFER, (short) selfNodeID, System.currentTimeMillis(), buffer.array());

            InetAddress address = InetAddress.getByName(peerIP);
            DatagramPacket sendPacket = new DatagramPacket(packet.convertToBytes(), packet.convertToBytes().length, address, port);
            sendSocket.send(sendPacket);

            System.out.println("Sent file: " + file.getName() + " to " + peerIP);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method for requesting the appropriate file from an appropriate node.
    private void requestFile(int nodeID, String fileName) {
        try {
            String message = "REQUEST:" + fileName;
            byte[] data = message.getBytes();

            System.out.println("Preparing to send file request for: " + fileName + " to node " + nodeID);

            InetAddress address = InetAddress.getByName(peers.get(nodeID).getIp());
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            sendSocket.send(packet);

            System.out.println("File request successfully sent for: " + fileName + " to " + peers.get(nodeID).getIp());

            System.out.println("Requested file: " + fileName + " from node " + nodeID);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to extract received file and save it right into the home directory.
    private void receiveFile(HacPacket packet) {
        byte[] data = packet.getData();

        if (data.length == 0) {
            System.out.println("Received an empty file transfer packet.");
            return;
        }

        int fileNameLength = data[0] & 0xFF;
        String fileName = new String(data, 1, fileNameLength);

        byte[] fileData = new byte[data.length - 1 - fileNameLength];
        System.arraycopy(data, 1 + fileNameLength, fileData, 0, fileData.length);

        File receivedFile = new File(pathToNodeHomeDir, fileName);
        try {
            Files.write(receivedFile.toPath(), fileData);
            System.out.println("Received and saved file: " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Allows us to delete a file from our home directory.
    // Connects to broadcastFileDeletion() in order to communicate with peers regarding the deletion.
    public void deleteFile(String fileName) {
        File file = new File(pathToNodeHomeDir, fileName);

        if (!file.exists()) {
            System.out.println("File does not exist: " + fileName);
            return;
        }

        if (file.delete()) {
            System.out.println("File deleted: " + fileName);
            broadcastFileDeletion(fileName);
        } else {
            System.out.println("Failed to delete file: " + fileName);
        }
    }

    // Tells all connected nodes that a file was deleted from a node.
    private void broadcastFileDeletion(String fileName) {
        if (peers.isEmpty()) {
            System.out.println("No peers found in config.json.");
            return;
        }

        try {
            String message = "DELETE:" + fileName;
            byte[] data = message.getBytes();

            for (Config.Node node : peers) {
                InetAddress address = InetAddress.getByName(node.getIp());
                DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
                sendSocket.send(packet);
                System.out.println("Notified " + node.getIp() + " about deleted file: " + fileName);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
