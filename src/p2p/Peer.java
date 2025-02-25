package p2p;

import java.net.*;
import java.io.*;
import java.sql.Array;
import java.util.*;
import com.google.gson.Gson; // For Jay: Make sure this library is installed, go to File > Project Structure > Libraries > hit the + > click on Maven > search up Gson Google and install the x.10 version, standard format to use JSON's in Java
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class Peer {
    private static final String pathToHomeDir = System.getProperty("user.dir") + "/p2p_home";
    private final HacP2P hac;
    private List<Config.Node> nodes;


    public Peer() {
        this.hac = new HacP2P(9876);

        Config config = ConfigHandler.loadConfig();
        if (config != null) {
            this.nodes = config.getNodes();
            System.out.println("Loaded " + nodes.size() + " peers from config.json");
        } else {
            this.nodes = new ArrayList<>();
            System.out.println("Warning: No peers loaded from config.json.");
        }
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
    public void sendHomeDir() {
        List<String> allFileNames = retrieveDirItems();

        if (allFileNames.isEmpty()) {
            System.out.println("There is nothing in the home directory.");
            return;
        }

        if (nodes.isEmpty()) {
            System.out.println("No peers found in config.json.");
            return;
        }

        for (Config.Node node : nodes) {
            hac.sendFileList(node.getIp(), allFileNames); // ✅ Send file list to peers
            System.out.println("Sent file list to " + node.getIp() + ":" + node.getPort());

            for (String fileName : allFileNames) {
                File file = new File(pathToHomeDir, fileName);
                hac.sendFile(node.getIp(), file); // ✅ Send each file
            }
        }
    }

    // Method so that when a file is added to home directory it is automatically sent
    public void addFile(File file) {
        File destFile = new File (pathToHomeDir, file.getName());

        try {
            if (!file.exists()) {
                System.out.println("File does not exist: " + file.getAbsolutePath());
                return;
            }

            Files.copy(file.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("File added: " + destFile.getAbsolutePath());

            sendHomeDir();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deleteFile(String fileName) {
        File file = new File(pathToHomeDir, fileName);

        if (!file.exists()) {
            System.out.println("File does not exist: " + fileName);
            return;
        }

        if (file.delete()) {
            System.out.println("File deleted: " + fileName);

            notifyPeersFileDeleted(fileName);
        } else {
            System.out.println("Failed to delete file: " + fileName);
        }
    }

    private void notifyPeersFileDeleted(String fileName) {
        if (nodes.isEmpty()) {
            System.out.println("No peers found in config.json.");
            return;
        }

        for (Config.Node node : nodes) {
            hac.sendFileDeletion(node.getIp(), fileName);
            System.out.println("Notified " + node.getIp() + " about deleted file: " + fileName);
        }
    }
}


