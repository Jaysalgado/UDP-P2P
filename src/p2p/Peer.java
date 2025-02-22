package p2p;

import java.net.*;
import java.io.*;
import java.util.*;
import com.google.gson.Gson; // For Jay: Make sure this library is installed, go to File > Project Structure > Libraries > hit the + > click on Maven > search up Gson Google and install the x.10 version, standard format to use JSON's in Java
import java.nio.file.Files;
import java.nio.file.Paths;

public class Peer {
    private DatagramSocket socket;
    private final int port;
    private final String pathToHomeDir = "home_directory.json"; // Grabs file that contains list of home dir

        public Peer(int port) {
            this.port = port;
            try {
                socket = new DatagramSocket(port);
                initHomeDirectory();
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }

        public void receive() {
            byte[] incomingData = new byte[1024];

            while (true) {
                try {
                    DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
                    socket.receive(incomingPacket);

                    String message = new String(incomingPacket.getData()).trim();
                    InetAddress IPAddress = incomingPacket.getAddress();
                    int port = incomingPacket.getPort();

                    System.out.println("Received message from client: " + message);
                    System.out.println("Client IP:" + IPAddress.getHostAddress());
                    System.out.println("Client port:" + port);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void send(String message, String ipAddress){
            try {
                byte[] data = message.getBytes();
                InetAddress address = InetAddress.getByName(ipAddress);
                DatagramPacket sendPacket = new DatagramPacket(data, data.length, address, port);
                socket.send(sendPacket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Method to create a home directory just in case there isn't one for the node
        private void initHomeDirectory() {
            File file = new File(pathToHomeDir);
            if (!file.exists()) {
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write("[]");
                    System.out.println("Home directory not detected. Created: ");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // Method to retrieve all items listed in the home directory
        public List<String> retrieveDirItems() {
            try {
                File file = new File(pathToHomeDir);
                if (!file.exists()) return new ArrayList<>();

                BufferedReader reader = new BufferedReader(new FileReader(file));
                return new Gson().fromJson(reader, List.class);
            } catch (IOException e) {
                e.printStackTrace();
                return new ArrayList<>();
            }
        }

        // Method to add new item to the home directory
        public void addItemToDir(String item) {
            try {
                List<String> items = retrieveDirItems();
                items.add(item);
                try (FileWriter writer = new FileWriter(pathToHomeDir)) {
                    writer.write(new Gson().toJson(items));
                }
            } catch (IOException e) {
                    e.printStackTrace();
                }
        }

        // Method to send home directory to peers
        public void sendHomeDir(String ipAddress) {
            try {
                String data = new String(Files.readAllBytes(Paths.get(pathToHomeDir)));
                send(data, ipAddress);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }


