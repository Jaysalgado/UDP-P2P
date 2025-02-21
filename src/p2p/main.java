package p2p;

public class Main {
    public static void main(String[] args) {
        Peer peer = new Peer(9876);
        new Thread(peer::receive).start();
        new Thread(() -> peer.send("Hello", "localhost")).start();
    }
}
