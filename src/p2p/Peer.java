package p2p;

import java.net.*;
import java.io.*;
import java.util.*;
import com.google.gson.Gson; // For Jay: Make sure this library is installed, go to File > Project Structure > Libraries > hit the + > click on Maven > search up Gson Google and install the x.10 version, standard format to use JSON's in Java
import java.nio.file.Files;
import java.nio.file.Paths;

public class Peer {

    public void start() {
        HacP2P hac = new HacP2P(9876);
        hac.activateHac();
    }

    // Method to create a home directory just in case there isn't one for the node
//    private void initHomeDirectory() {
//        File file = new File(pathToHomeDir);
//        if (!file.exists()) {
//            try (FileWriter writer = new FileWriter(file)) {
//                writer.write("[]");
//                System.out.println("Home directory not detected. Created: ");
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//    }
//
//    // Method to retrieve all items listed in the home directory
//    public List<String> retrieveDirItems() {
//        try {
//            File file = new File(pathToHomeDir);
//            if (!file.exists()) return new ArrayList<>();
//
//            BufferedReader reader = new BufferedReader(new FileReader(file));
//            return new Gson().fromJson(reader, List.class);
//        } catch (IOException e) {
//            e.printStackTrace();
//            return new ArrayList<>();
//        }
//    }
//
//    // Method to add new item to the home directory
//    public void addItemToDir(String item) {
//        try {
//            List<String> items = retrieveDirItems();
//            items.add(item);
//            try (FileWriter writer = new FileWriter(pathToHomeDir)) {
//                writer.write(new Gson().toJson(items));
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//
//    // Method to send home directory to peers
//    public void sendHomeDir(String ipAddress) {
//        try {
//            String data = new String(Files.readAllBytes(Paths.get(pathToHomeDir)));
//            send(data, ipAddress);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }


}


