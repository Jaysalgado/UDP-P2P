package p2p;

import java.util.List;

public class Config {
    private String selfIP;
    private List<Node> peers;

    public List<Node> getPeers() { return peers; }

    public String getSelfIP() { return selfIP; }

    public static class Node {
        private int id;
        private String ip;
        private int port;

        public int getId() { return id; }

        public String getIp() { return ip; }

        public int getPort() { return port; }
    }
}
