package p2p;

import java.net.*;
import java.net.DatagramSocket;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
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
import java.nio.file.WatchService;
import java.nio.file.FileSystems;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.Path;
import java.nio.file.Paths;


public class HacP2P {
    private static final String RESET = "\033[0m";  // Reset color
    private static final String PINK = "\033[95m";  // Heartbeats
    private static final String RED = "\033[91m";   // Dead clients
    private static final String GREEN = "\033[92m"; // Recovered clients
    private static final String DARK_GREEN = "\033[32m"; // File updates
    private static final String LIGHT_RED = "\033[31m";  // File deletes
    private static final String BLUE = "\033[94m";  // File transfers
    private DatagramSocket sendSocket;
    private DatagramSocket receiveSocket;
    private final int port;
    private final String pathToNodeHomeDir = System.getProperty("user.dir") + "/p2p_home";
    private List<Config.Node> peers;
    private final String myIP;
    private final int selfNodeID;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private final Set<String> recentlyBroadcastedFiles = new HashSet<>();
    private ExecutorService messageHandlerPool = Executors.newFixedThreadPool(6);
    public HashMap<Integer, String> activePeers = new HashMap<>();
    private HashMap<Integer, Long> lastHeartbeat = new HashMap<>();
    private HashMap<String, Long> deletedFiles = new HashMap<>();
    private static HashMap<Integer, List<String>> nodeFiles = new HashMap<>();

    public HacP2P (int port, List<Config.Node> peers, String myIP){
        this.port = port;
        this.myIP = myIP;
        this.peers = peers != null ? peers : new ArrayList<>();

        this.selfNodeID = peers.stream()
                .filter(node -> node.getIp().equals(myIP))
                .map(Config.Node::getId)
                .findFirst()
                .orElse(-1);
        if (this.selfNodeID == -1) {
            System.out.println(RED + "Error: Node ID not found for IP: " + myIP + RESET);
        }

        try {
            this.receiveSocket = new DatagramSocket(port);
            this.sendSocket = new DatagramSocket();
        } catch (Exception e) {
            e.printStackTrace();
        }
        initHomeDirectory();
    }

    public void activateHac () {
        new Thread(this::watchDirectoryForChanges).start();
        new Thread (this::startHeartbeats).start();
        new Thread(this::listen).start();
        scheduler.scheduleAtFixedRate(this::isAlive, 5, 15, TimeUnit.SECONDS);
    }

    private void startHeartbeats ()  {

        for (Config.Node peer : peers) {

            String peerIP = peer.getIp();
            if (peerIP.equals(myIP)) {
                continue;
            }

            int interval = secureRandom.nextInt(31) + 1;

            scheduler.scheduleAtFixedRate(() -> {
                List<String> allFileNames = retrieveDirItems();
                List<String> filteredFileNames = allFileNames.stream()
                        .filter(fileName -> !fileName.equalsIgnoreCase("config.json"))
                        .toList();
                nodeFiles.put(this.selfNodeID, filteredFileNames);
                String fileListJson = new Gson().toJson(filteredFileNames);
                byte[] data = fileListJson.getBytes();

                sendHeartbeats(peerIP, data);
            }, 0, interval, TimeUnit.SECONDS);
        }

    }

    private void sendHeartbeats (String peerIP, byte[] data) {

        activePeers.put(this.selfNodeID, "ACTIVE");

        try {
            HacPacket protocol = new HacPacket(HacPacket.TYPE_HEARTBEAT, (short) this.selfNodeID, System.currentTimeMillis(), data);
            byte[] packet = protocol.convertToBytes();
            InetAddress address = InetAddress.getByName(peerIP);
            DatagramPacket sendPacket = new DatagramPacket(packet, packet.length, address, port);
            sendSocket.send(sendPacket);
//            System.out.println("Sent heartbeat to: " + peerIP);
        } catch (java.net.NoRouteToHostException e) {
            System.err.println(RED + "Error sending heartbeat: Unable to reach host - no route available for ip: " + peerIP + RESET);
        }  catch (java.net.SocketException e) {
                System.err.println(RED + "Error sending heartbeat: Host is down at ip: " + peerIP + RESET);

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

    private void routeMessages(DatagramPacket incomingPacket) {
        try {
            InetAddress senderIP = incomingPacket.getAddress();

            if (incomingPacket.getLength() < 16) {
                System.out.println("Error: Received packet is too small (" + incomingPacket.getLength() + " bytes). Ignoring.");
                return;
            }

            byte[] receivedBytes = Arrays.copyOf(incomingPacket.getData(), incomingPacket.getLength());

            HacPacket packet = HacPacket.convertFromBytes(receivedBytes);
            if (packet == null) {
                System.out.println("Error: Packet conversion failed. Data may be corrupt.");
                return;
            }

            int senderPort = incomingPacket.getPort();

            if (packet.getNodeID() >= 0 && packet.getNodeID() < peers.size()) {
//                System.out.println("Sender's IP: " + peers.get(packet.getNodeID()).getIp());
//                System.out.println("Sender's Port: " + senderPort);
            } else {
                System.out.println("Packet received from unknown: " + packet.getNodeID());
            }

            switch (packet.getType()) {
                case HacPacket.TYPE_HEARTBEAT:
                    checkHeartbeats(packet);
                    compareFileLists(packet);
                    break;

                case HacPacket.TYPE_FILELIST:
//                    System.out.println("Received file list from node " + packet.getNodeID());
                    compareFileLists(packet);
                    break;

                case HacPacket.TYPE_FILEUPDATE:
//                    System.out.println("Received file update request.");
                    break;

                case HacPacket.TYPE_FILEDELETE:
//                    System.out.println("Received file delete request.");

                    String deleteDataString = new String(packet.getData(), java.nio.charset.StandardCharsets.UTF_8);

                    String fileToDelete = deleteDataString.substring("DELETE:".length()).trim();
                    deleteFile(fileToDelete);
                    break;

                case HacPacket.TYPE_FILETRANSFER:
//                    System.out.println("Received file transfer packet.");
                    String transferDataString = new String(packet.getData());

                    if (transferDataString.startsWith("REQUEST:")) {
                        String fileName = transferDataString.substring(8).trim();
                        System.out.println(BLUE + "File request received for: " + fileName + RESET);
                        sendFile(senderIP.getHostAddress(), new File(pathToNodeHomeDir, fileName));
                    } else {
//                        System.out.println("Receiving actual file data...");
                        receiveFile(packet);
                    }
                    break;

                default:
                    System.out.println("Received unknown packet type: " + packet.getType());
                    break;
            }

        } catch (Exception e) {
            System.out.println("Unable to process incoming packet.");
            e.printStackTrace();
        }
    }

    private void checkHeartbeats (HacPacket packet) {
//        System.out.println(GREEN + "Received heartbeat from node: " + packet.getNodeID() + RESET);
        activePeers.put((int) packet.getNodeID(), "ACTIVE");
        nodeFiles.put((int) packet.getNodeID(), packet.getData() != null ? new Gson().fromJson(new String(packet.getData()), List.class) : new ArrayList<>());
        lastHeartbeat.put((int) packet.getNodeID(), System.currentTimeMillis());
    }

    private void isAlive() {
        long currentTime = System.currentTimeMillis();
        String timestamp = "=== PEER STATUS AT " + new java.util.Date(currentTime) + " ===";
        int lineLength = timestamp.length();
        String separator = "=".repeat(lineLength); // Dynamically match separator length

        System.out.println("\n" + GREEN + timestamp + RESET);

        // Mark inactive nodes based on heartbeat timeout
        for (int nodeID : lastHeartbeat.keySet()) {
            if (currentTime - lastHeartbeat.get(nodeID) > 31000) {
                activePeers.put(nodeID, "INACTIVE");
            }
        }

        // Print status of each node
        for (int nodeID : activePeers.keySet()) {
            String status = activePeers.get(nodeID);
            String color = status.equals("ACTIVE") ? GREEN : RED;

            if (status.equals("ACTIVE")) {
                List<String> files = nodeFiles.getOrDefault(nodeID, new ArrayList<>());
                System.out.println(color + "Node " + nodeID + " files: " + RESET + BLUE + files + RESET);
            } else {
                System.out.println(color + "Node " + nodeID + " is INACTIVE" + RESET);
            }
        }

        System.out.println("\n" + GREEN + separator + RESET);
    }


    // Checks to see if home directory exists, and if not, initializes it.
    private void initHomeDirectory() {
        File path = new File(pathToNodeHomeDir);
        if (!path.exists()) {
            path.mkdirs();
            System.out.println(GREEN + "Home directory not detected. Created: " + pathToNodeHomeDir + RESET);
        }
    }

    // Retrieves the file names in the home directory.
    public List<String> retrieveDirItems() {
        File dir = new File(pathToNodeHomeDir);
        if (!dir.exists() || !dir.isDirectory()) {
            System.out.println(RED + "Home directory does not exist." + RESET);
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

    // Retrieves the file list for that specific node and sends it to all connected nodes for comparison.
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
//                System.out.println("Sent file list to: " + node.getIp());
            }
        } catch (java.net.NoRouteToHostException e) {
            System.err.println(RED + "Error: Unable to reach host - no route available "  + RESET);
        }  catch (java.net.SocketException e) {
            System.err.println(RED + "Error: Host is down " + RESET);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Allows a node to compare the file list it receives with its own file list and send a request for missing files.
    private void compareFileLists(HacPacket packet) {
        byte[] data = packet.getData();
        if (data.length == 0) {
//            System.out.println("Received an empty file list.");
            return;
        }

        String jsonString = new String(data);
        List<String> receivedFileList = new Gson().fromJson(jsonString, List.class);
        List<String> localFiles = retrieveDirItems();


        // For the sake of this project, we assumed the file names would be homogenous between node directories
        // but implemented failsafe just in case files were named differently. Sets to lower-case for consistency.
        Set<String> receivedFileSet = new HashSet<>(receivedFileList);

        Set<String> localFileSet = new HashSet<>(localFiles);

        List<String> filesToDownload = new ArrayList<>();
        List<String> filesToBroadcast = new ArrayList<>();

        for (String fileName : localFileSet) {
            if (!fileName.equalsIgnoreCase("config.json") && !receivedFileSet.contains(fileName)) {
                if (recentlyBroadcastedFiles.contains(fileName)) {
//                    System.out.println("Skipping redundant broadcast for: " + fileName);
                    continue;
                }
//                System.out.println("New file detected: " + fileName + " - Broadcasting update to peers.");
                filesToBroadcast.add(fileName);
                recentlyBroadcastedFiles.add(fileName);
            }
        }

        for (String fileName : receivedFileSet) {
            if (!localFileSet.contains(fileName) && !deletedFiles.containsKey(fileName)) {  // Prevent re-requesting deleted files
                System.out.println(LIGHT_RED + "Missing file detected: " + fileName + " - Requesting from Node " + packet.getNodeID() + RESET);
                filesToDownload.add(fileName);
            }
        }

        for (String fileName : filesToDownload) {
            requestFile(packet.getNodeID(), fileName);
        }

        if (!filesToBroadcast.isEmpty()) {
            sendFileList();
        }
    }

    // Method to read a file, use HacPacket to transform it into a message and send it to the node that requires it.
    public void sendFile(String peerIP, File file) {
        if (!file.exists()) {
            System.out.println(RED + "Error: File does not exist on this node. Cannot send: " + file.getName() + RESET);
            return;
        }

        try {
            byte[] fileData = Files.readAllBytes(file.toPath());
            byte[] fileNameBytes = file.getName().getBytes();

            if (fileData.length == 0) {
                System.out.println(RED + "Warning: Sending empty file -> " + file.getName() + RESET);
                fileData = new byte[]{0};
            }

            int totalSize = 1 + fileNameBytes.length + fileData.length;
            ByteBuffer buffer = ByteBuffer.allocate(totalSize);
            buffer.put((byte) fileNameBytes.length);
            buffer.put(fileNameBytes);
            buffer.put(fileData);

            byte[] finalPacketData = new byte[buffer.position()];
            buffer.rewind();
            buffer.get(finalPacketData);

            HacPacket packet = new HacPacket(HacPacket.TYPE_FILETRANSFER, (short) selfNodeID, System.currentTimeMillis(), finalPacketData);

            InetAddress address = InetAddress.getByName(peerIP);
            byte[] packetBytes = packet.convertToBytes();
            DatagramPacket sendPacket = new DatagramPacket(packetBytes, packetBytes.length, address, port);
            sendSocket.send(sendPacket);

//            System.out.println("Successfully sent file: " + file.getName() + " to " + peerIP);

        } catch (java.net.NoRouteToHostException e) {
            System.err.println(RED + "Error: Unable to reach host - no route available " + RESET);
        }  catch (java.net.SocketException e) {
            System.err.println(RED + "Error: Host is down "  + RESET);

        } catch (IOException e) {
            System.out.println(RED + "Error sending file: " + file.getName() + RESET);
            e.printStackTrace();
        }
    }

    // Method for requesting the appropriate file from an appropriate node.
    private void requestFile(int nodeID, String fileName) {
        try {
            String message = "REQUEST:" + fileName;
            byte[] requestData = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            HacPacket requestPacket = new HacPacket(
                    HacPacket.TYPE_FILETRANSFER,
                    (short) selfNodeID,
                    System.currentTimeMillis(),
                    requestData
            );

            byte[] packetBytes = requestPacket.convertToBytes();

            InetAddress address = InetAddress.getByName(peers.get(nodeID).getIp());
            DatagramPacket sendPacket = new DatagramPacket(packetBytes, packetBytes.length, address, port);
            sendSocket.send(sendPacket);

//            System.out.println("File request successfully sent for: " + fileName + " to " + peers.get(nodeID).getIp());
//            System.out.println("Requested file: " + fileName + " from node " + nodeID);
        } catch (java.net.NoRouteToHostException e) {
            System.err.println(RED + "Error when requesting file: Unable to reach host - no route available for node: " + nodeID + RESET);
        }  catch (java.net.SocketException e) {
            System.err.println(RED + "Error when requesting file: Host is down at nodeID: " + nodeID + RESET);

        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to extract received file and save it right into the home directory.
    private void receiveFile(HacPacket packet) {
        byte[] data = packet.getData();

        if (data.length < 1) {
            System.out.println(RED + "Received an empty or corrupted file transfer packet. Ignoring." + RESET);
            return;
        }

        int fileNameLength = data[0] & 0xFF;
        if (data.length < 1 + fileNameLength) {
            System.out.println(RED + "Error: Packet corrupted. File name length exceeds packet size." + RESET);
            return;
        }

        String fileName = new String(data, 1, fileNameLength);
//        System.out.println("Receiving file: " + fileName);

        byte[] fileData = new byte[data.length - 1 - fileNameLength];
        System.arraycopy(data, 1 + fileNameLength, fileData, 0, fileData.length);

        File receivedFile = new File(pathToNodeHomeDir, fileName);
        try {
            Files.write(receivedFile.toPath(), fileData);
            System.out.println(GREEN + "Successfully received and saved file: " + fileName + RESET);
        } catch (IOException e) {
            System.out.println(RED + "Error writing received file: " + fileName + RESET);
            e.printStackTrace();
        }
    }

    // Allows us to delete a file from our home directory.
    // Connects to broadcastFileDeletion() in order to communicate with peers regarding the deletion.
    public void deleteFile(String fileName) {
        File file = new File(pathToNodeHomeDir, fileName);

        if (!file.exists()) {
//            System.out.println("File does not exist: " + fileName);
            return;
        }

        if (file.delete()) {
            System.out.println(GREEN + "File deleted: " + fileName + RESET);
            deletedFiles.put(fileName, System.currentTimeMillis()); // Store deletion time
            broadcastFileDeletion(fileName);
        } else {
            System.out.println(RED + "Failed to delete file: " + fileName + RESET);
        }
    }

    // Tells all connected nodes that a file was deleted from a node.
    private void broadcastFileDeletion(String fileName) {
        if (peers.isEmpty()) {
            System.out.println(RED + "No peers found in config.json." + RESET);
            return;
        }
        try {
            String message = "DELETE:" + fileName;
            byte[] data = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            HacPacket packet = new HacPacket(HacPacket.TYPE_FILEDELETE, (short) selfNodeID, System.currentTimeMillis(), data);
            byte[] packetBytes = packet.convertToBytes();

            for (Config.Node node : peers) {
                if (node.getIp().equals(myIP)) {
                    continue;
                }
                InetAddress address = InetAddress.getByName(node.getIp());
                DatagramPacket datagramPacket = new DatagramPacket(packetBytes, packetBytes.length, address, port);
                sendSocket.send(datagramPacket);
//                System.out.println("Notified " + node.getIp() + " about deleted file: " + fileName);
            }
        } catch (java.net.NoRouteToHostException e) {
            System.err.println(RED + "Error: Unable to reach host - no route available "  + RESET);
        }  catch (java.net.SocketException e) {
            System.err.println(RED + "Error: Host is down " + RESET);

        }catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void watchDirectoryForChanges() {
        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();

            Path dirPath = Paths.get(pathToNodeHomeDir);

            dirPath.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE
            );

            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    WatchEvent.Kind<?> kind = ev.kind();
                    Path fileName = ev.context();

                    File changedFile = new File(pathToNodeHomeDir, fileName.toString());

                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        System.out.println("New file detected: " + changedFile.getName());
                        addFile(changedFile);
                    }
                    else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        System.out.println("File deleted: " + changedFile.getName());
                        deleteFile(changedFile.getName());
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    break;
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
