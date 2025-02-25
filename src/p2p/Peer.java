package p2p;

import java.net.*;
import java.io.*;
import java.util.*;
import com.google.gson.Gson; // For Jay: Make sure this library is installed, go to File > Project Structure > Libraries > hit the + > click on Maven > search up Gson Google and install the x.10 version, standard format to use JSON's in Java


public class Peer {
    private static final String pathToHomeDir = System.getProperty("user.dir") + "/p2p_home";
    private final HacP2P hac;

    public Peer() {
        this.hac = new HacP2P(9876);
    }

    public void start() {
        hac.activateHac();
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
        File dir = new File(pathToHomeDir);

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

    // Method to send home directory to peers
    public void sendHomeDir(String ipAddress) {
        List<String> allFileNames = retrieveDirItems();

        if (allFileNames.isEmpty()) {
            System.out.println("There is nothing in the home directory.");
            return;
        }

        hac.sendFileList(ipAddress, allFileNames);
    }
}


