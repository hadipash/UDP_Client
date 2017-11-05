class MyDatagram {
    // Header - 15 bytes
    private byte[] flags;       // 0 - SYN, 1 - ACK, 2 - FIN
    private int seqNum;         // 32-bit sequence number
    private int ackNum;         // 32-bit acknowledgment sequence number
    private short checksum;     // 16-bit checksum
    private short length;       // 16-bit length of payload
    // Payload
    private byte[] data;
    // Full datagram represented in bytes
    private byte[] datagram;

    MyDatagram(byte[] _datagram) {
        datagram = _datagram;
        GenerateChecksum();
        // if a packet is not corrupted, then extract data from it
        if (checksum == 0) {
            flags = new byte[]{_datagram[0], _datagram[1], _datagram[2]};
            seqNum = (_datagram[3]&0x000000FF) + ((_datagram[4] << 8)&0x0000FF00) + ((_datagram[5] << 16)&0x00FF0000) +
                    ((_datagram[6] << 24)&0xFF000000);
            ackNum = (_datagram[7]&0x000000FF) + ((_datagram[8] << 8)&0x0000FF00) + ((_datagram[9] << 16)&0x00FF0000) +
                    ((_datagram[10] << 24)&0xFF000000);
            length = (short)((_datagram[13]&0x00FF) + ((_datagram[14] << 8)&0xFF00));
            if (length > 0) {
                data = new byte[length];
                System.arraycopy(_datagram, 15, data, 0, length);
            }
        }
    }

    MyDatagram(byte SYN, byte ACK, byte FIN, int _seqNum, int _ackNum, byte[] _data) {
        flags = new byte[]{SYN, ACK, FIN};
        seqNum = _seqNum;
        ackNum = _ackNum;
        data = _data;
        length = (_data == null ? 0 : (short)_data.length);
        checksum = 0;
        ConvertToBytes();
        GenerateChecksum();
    }

    private void ConvertToBytes() {
        datagram = new byte[(length == 0) ? 15 : 15 + data.length];
        datagram[0] = flags[0];
        datagram[1] = flags[1];
        datagram[2] = flags[2];
        datagram[3] = (byte) seqNum;
        datagram[4] = (byte)(seqNum >> 8);
        datagram[5] = (byte)(seqNum >> 16);
        datagram[6] = (byte)(seqNum >> 24);
        datagram[7] = (byte) ackNum;
        datagram[8] = (byte)(ackNum >> 8);
        datagram[9] = (byte)(ackNum >> 16);
        datagram[10] = (byte)(ackNum >> 24);
        datagram[11] = (byte) checksum;
        datagram[12] = (byte)(checksum >> 8);
        datagram[13] = (byte) length;
        datagram[14] = (byte)(length >> 8);
        if (length > 0)
            System.arraycopy(data,0, datagram,15, length);
    }

    private void GenerateChecksum() {
        long sum = 0;
        int i = 0;

        while (i < datagram.length){
            sum += (datagram[i++]&0xff) << 8;
            if (i == datagram.length) break;
            sum += (datagram[i++]&0xff);
        }

        sum = (sum&0xFFFF) + (sum >> 16);
        checksum = (short)((~((sum&0xFFFF) + (sum >> 16)))&0xFFFF);

        // update datagram
        datagram[11] = (byte) checksum;
        datagram[12] = (byte)(checksum >> 8);
    }

    boolean Verify() { return (checksum == 0); }

    boolean SYN(){ return (flags[0] == 1); }

    boolean ACK() { return (flags[1] == 1); }

    boolean FIN () { return (flags[2] == 1); }

    int SeqNum() { return seqNum; }

    int ACKNum() { return ackNum; }

    byte[] GetData() { return data; }

    byte[] GetDatagram() { return datagram; }
}
