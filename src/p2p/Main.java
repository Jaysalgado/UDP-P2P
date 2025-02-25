package p2p;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Peer peer = new Peer();
        peer.start();

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("Enter a command ('send' to send file list, 'exit' to quit): ");
            String command = scanner.nextLine();

            if (command.equalsIgnoreCase("send")) {
                System.out.print("Enter peer IP address: ");
                String peerIPAddress = scanner.nextLine();
                peer.sendHomeDir(peerIPAddress); // ✅ Send file list when user enters 'send'
            } else if (command.equalsIgnoreCase("exit")) {
                System.out.println("Exiting...");
                break;
            } else {
                System.out.println("Unknown command.");
            }
        }
        scanner.close();
    }
}
