package p2p;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Peer peer = new Peer();
        peer.start();

        Scanner readInputs = new Scanner(System.in);

        while (true) {
            System.out.println("Enter a command:");
            System.out.println("'send' to deliver file list, 'add' to add file, 'delete' to delete file' or 'exit' to quit");
            String command = readInputs.nextLine();

            if (command.equalsIgnoreCase("send")) {
                System.out.print("File Directory sent.");
            } else if (command.equalsIgnoreCase("add")) {
                System.out.print("Enter file path to add: ");
            } else if (command.equalsIgnoreCase("delete")) {
                System.out.print("Enter file name to delete: ");
            } else if (command.equalsIgnoreCase("exit")) {
                System.out.print("Exiting interface.");
                break;
            } else {
                System.out.println("Unable to parse command.");
            }
        }
        readInputs.close();
    }
}
