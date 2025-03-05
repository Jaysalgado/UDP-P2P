package p2p;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class Peer {
    private final HacP2P hac;
    private List<Config.Node> peers;
    private Thread udpReceiverThread;


    public Peer() {
        Config config = ConfigHandler.loadConfig();

        if (config != null && config.getPeers() != null) {
            this.peers = config.getPeers();
            System.out.println("Loaded " + peers.size() + " peers from config.json.");
        } else {
            this.peers = new ArrayList<>();
            System.out.println("Warning: No peers loaded from config.json.");
        }

        this.hac = new HacP2P(9876, peers, config != null ? config.getSelfIP() : "127.0.0.1");

        startWsUdpReceiver();
    }

    private void startWsUdpReceiver() {
        udpReceiverThread = new Thread(() -> {
            WsUdpReceiver udpReceiver = new WsUdpReceiver(this); // Pass Peer instance
            udpReceiver.start(); // Start the UDP server
        });

        udpReceiverThread.setDaemon(true);
        udpReceiverThread.start();
    }

    public void start() {
        hac.activateHac();
    }

    public void addFile(String filePath) {
        File file = new File(filePath);
        hac.addFile(file);
    }

    public void deleteFile(String fileName) {
        hac.deleteFile(fileName);
    }

    public void sendHomeDirectory() {
        hac.sendFileList();
    }

    public List<String> listFiles() {
        return hac.retrieveDirItems();
    }

    public HashMap<Integer, String> getPeers() {
        return hac.activePeers;
    }

}