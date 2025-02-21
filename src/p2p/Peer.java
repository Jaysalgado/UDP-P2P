package p2p;

import java.net.*;
import java.io.*;
import java.util.*;

public class Peer {

    private DatagramSocket socket;
    private int port;

        public Peer(int port) {
            this.port = port;
            try {
                socket = new DatagramSocket(port);
            } catch (SocketException e) {
                e.printStackTrace();
            }

        }

        public void recieve() {


            byte[] incomingData = new byte[1024];

            while(true) {
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

        public void send (String message, String ipAddress){
            try {
                byte[] data = message.getBytes();
                InetAddress address = InetAddress.getByName(ipAddress);
                DatagramPacket sendPacket = new DatagramPacket(data, data.length, address, port);
                socket.send(sendPacket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


