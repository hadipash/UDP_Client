import java.io.*;
import java.net.*;
import java.util.*;

public class Client {
    // 1460 (total size of the datagram) - 15 (size of the header) bytes is the maximum size of payload
    private static final int PAYLOAD = 1460 - 15;
    private static final int TIMEOUT = 100; // Retransmission timeout (if ACK was not received)
    private static byte buf[];
    private static int seqNum;
    private static int ackNum;
    private static int totalPackets;        // Total number of sent packets
    private static int lostPackets;         // Total number of lost/corrupted packets
    private static long timer;              // Stopwatch
    private static FileInputStream file;

    private static DatagramSocket socket;
    private static DatagramPacket receivePacket;
    private static DatagramPacket sendPacket;

    public static void main(String[] args) {
        new Thread(()->{
            try {
                // Create a client socket
                socket = new DatagramSocket();

                // Create a buffer for input and output packets
                buf = new byte[1460];
                sendPacket = new DatagramPacket(buf, buf.length, InetAddress.getLocalHost(), 8000);
                receivePacket = new DatagramPacket(buf, buf.length);

                // Read a file to send
                file = new FileInputStream("dcn.pdf");
                byte[] data = file.readAllBytes();
                file.close();

                // initialize other variables
                seqNum = 0;
                totalPackets = 0;
                lostPackets = 0;
                ackNum = 0;
                timer = System.currentTimeMillis();

                // Connect to the server
                connect();

                // Split the file into datagrams and send them one by one
                int pos = 0;
                while (pos < data.length){
                    byte[] dts;   // create payload
                    int newPos;

                    if (PAYLOAD < (data.length - pos)) {
                        dts = new byte[PAYLOAD];
                        newPos = pos + dts.length;
                    }
                    else {
                        newPos = data.length;
                        dts = new byte[newPos - pos];
                    }

                    System.arraycopy(data, pos, dts, 0, newPos - pos);

                    boolean delivered = false;
                    while(!delivered) {
                        // Send data
                        SendPacket((byte) 0, (byte) 0, (byte) 0, seqNum, 0, dts);

                        // receive ACK
                        try {
                            Arrays.fill(buf, (byte)0);
                            socket.receive(receivePacket);
                            parsePacket();

                            if(ackNum == ~seqNum) {
                                System.out.println("Packet with sequence number " + seqNum + " was delivered");
                                seqNum = ~seqNum;
                                delivered = true;
                            }
                        } catch (SocketTimeoutException ex) {
                            System.out.println("Packet with sequence number " + seqNum +
                                    " was lost or corrupted. Retransmission...");
                            lostPackets++;
                        }
                    }

                    pos = newPos;
                }

                // Disconnect from the sever
                disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void parsePacket() throws IOException {
        // reset timeout to infinity
        socket.setSoTimeout(0);

        MyDatagram dg = new MyDatagram(receivePacket.getData());

        if (dg.Verify()){
            if (dg.SYN() && dg.ACK()) {
                ackNum = dg.ACKNum();
                // Send ACK
                if (ackNum == ~seqNum)
                    SendPacket((byte) 0, (byte) 1, (byte) 0, 0, ~dg.SeqNum(), null);
            }
            else if(dg.FIN()) {
                ackNum = dg.SeqNum();
                // Send ACK
                SendPacket((byte) 0, (byte) 1, (byte) 0, 0, ~dg.SeqNum(), null);
            }
            else if(dg.ACK())
                ackNum = dg.ACKNum();

            // Assume for simplicity, that the server doesn't send any data
        }
        else
            System.out.println("Corrupted packet: drop.");
    }

    private static void connect() throws IOException {
        System.out.println("Connecting to the server...");

        boolean connected = false;
        while(!connected) {
            // Send SYN
            SendPacket((byte) 1, (byte) 0, (byte) 0, seqNum, 0, null);

            // receive SYN + ACK
            try {
                Arrays.fill(buf, (byte)0);
                socket.receive(receivePacket);
                parsePacket();

                if(ackNum == ~seqNum) {
                    System.out.println("Connection with the server has been established successfully.\n");
                    connected = true;
                    seqNum = ~seqNum;
                }
            } catch (SocketTimeoutException ex) {
                lostPackets++;
            }
        }
    }

    private static void disconnect() throws IOException {
        System.out.println("\nDisconnecting from the server...");

        // Send Fin and receive ACK
        boolean disconnected = false;
        while(!disconnected) {
            SendPacket((byte)0, (byte)0, (byte)1, seqNum, 0, null);

            try {
                Arrays.fill(buf, (byte)0);
                socket.receive(receivePacket);
                parsePacket();

                if (ackNum == ~seqNum) {
                    disconnected = true;
                    seqNum = ~seqNum;
                }
            } catch (SocketTimeoutException ex) {
                lostPackets++;
            }
        }

        disconnected = false;

        // receive FIN and send ACK
        while(!disconnected) {
            try {
                Arrays.fill(buf, (byte)0);
                socket.receive(receivePacket);
                parsePacket();

                System.out.println("Connection with the server has been terminated successfully.");
                System.out.println("Total number of sent packets: " + totalPackets +
                        ". Number of lost/corrupted packets: " + lostPackets + " (" +
                        String.format("%.02f%%).", ((lostPackets / (double)totalPackets) * 100)));
                System.out.println("Transmission time: " +
                        (int)((System.currentTimeMillis() - timer) / (double)1000) + "s.");
                disconnected = true;
            } catch (SocketTimeoutException ex) {
                lostPackets++;
            }
        }

        // Close socket
        socket.close();
    }

    private static void SendPacket(byte SYN, byte ACK, byte FIN, int sn, int an, byte[] d) throws IOException {
        totalPackets++;
        MyDatagram udp = new MyDatagram(SYN, ACK, FIN, sn, an, d);

        // Every 10th packet - lost
        // Every 20th packet - corrupted
        // Except every 200th packet
        if (totalPackets % 200 == 0 || (totalPackets % 10 != 0)) {
            sendPacket.setData(udp.GetDatagram());
            socket.send(sendPacket);
            socket.setSoTimeout(TIMEOUT);
        }
        else if (totalPackets % 20 == 0) {
            byte[] temp = udp.GetDatagram();
            Random random = new Random();

            // Corrupt up to 10 random entries in datagram
            for (int i = 0; i < random.nextInt(11); i++)
                temp[random.nextInt(temp.length)] = (byte)random.nextInt(100);

            sendPacket.setData(temp);
            socket.send(sendPacket);
            socket.setSoTimeout(TIMEOUT);
        }
        else {
            sendPacket.setAddress(InetAddress.getByName("google.com")); // Packet lost
            sendPacket.setData(udp.GetDatagram());
            socket.send(sendPacket);
            socket.setSoTimeout(TIMEOUT);
            sendPacket.setAddress(InetAddress.getLocalHost());          // Set back initial address
        }
    }
}
