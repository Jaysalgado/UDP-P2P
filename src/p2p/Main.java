package p2p;

import java.util.Scanner;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        Peer peer = new Peer();
        peer.start();

        Scanner readInputs = new Scanner(System.in);

        while (true) {
            System.out.println("Enter a command:");
            System.out.println("'send' to deliver file list, 'add' to add file, 'delete' to delete file' or 'exit' to quit");
            System.out.println("'retrieve' grabs current file listing for this node.");
            String command = readInputs.nextLine();

            if (command.equalsIgnoreCase("send")) {
                peer.sendHomeDirectory();
                System.out.print("File Directory sent.");
            } else if (command.equalsIgnoreCase("add")) {
                System.out.print("Enter file path to add: ");
                String filePath = readInputs.nextLine().trim();
                File file = new File(filePath);
                if (file.exists()) {
                    peer.addFile(filePath);
                    System.out.println("File added: " + filePath);
                } else {
                    System.out.println("Error: File does not exist.");
                }
            } else if (command.equalsIgnoreCase("delete")) {
                System.out.print("Enter file name to delete: ");
                String fileName = readInputs.nextLine().trim();
                peer.deleteFile(fileName);
                System.out.println("Requested file deletion: " + fileName);
            } else if (command.equalsIgnoreCase("exit")) {
                System.out.print("Exiting interface.");
                break;
            } else if (command.equalsIgnoreCase("retrieve")) {
                peer.retrieveMyList();
            } else {
                System.out.println("Unable to parse command.");
            }
        }
        readInputs.close();
    }
}
