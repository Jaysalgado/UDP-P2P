package p2p;

import java.util.List;

public class Config {
    private String selfIP;
    private List<Node> peers;

    // Returns our list of peers
    public List<Node> getPeers() { return peers; }

    // Returns value of the node's own IP
    public String getSelfIP() { return selfIP; }

    public static class Node {
        private int id;
        private String ip;
        private int port;

        // Return node ID
        public int getId() { return id; }

        // Returns connected nodes IP address
        public String getIp() { return ip; }

        public int getPort() { return port; }
    }
}
