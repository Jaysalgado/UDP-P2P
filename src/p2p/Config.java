package p2p;

import java.util.List;

public class Config {
    private List<Node> nodes;

    public List<Node> getNodes() {
        return nodes;
    }

    public static class Node {
        private int id;
        private String ip;
        private int port;

        public int getId() {
            return id;
        }

        public String getIp() {
            return ip;
        }

        public int getPort() {
            return port;
        }
    }
}
