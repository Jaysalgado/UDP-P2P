package p2p;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class HacPacket {
    public static final byte VERSION_NUM = 1;
    public static final byte TYPE_HEARTBEAT = 0x01;
    public static final byte TYPE_FAILURE = 0x02;
    public static final byte TYPE_RECOVERY = 0x03;
    public static final byte TYPE_FILELIST = 0x04;
    public static final byte TYPE_FILEUPDATE = 0x05;
    public static final byte TYPE_FILEDELETE = 0x06;
    public static final byte TYPE_FILETRANSFER = 0x07;

    private byte version;
    private byte type;
    private short nodeID;
    private long timestamp;
    private int length;
    private byte[] data;

    public HacPacket(byte type, short nodeID, long timestamp, byte[] data) {
        this.version = VERSION_NUM;
        this.type = type;
        this.nodeID = nodeID;
        this.timestamp = timestamp;
        this.length = (data == null) ? 0 : data.length;
        this.data = (data == null) ? new byte[0] : data;
    }

    public byte[] convertToBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(16 + length);
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.put(version);
        buffer.put(type);
        buffer.putShort(nodeID);
        buffer.putInt((int) (timestamp >> 32));
        buffer.putInt((int) timestamp);
        buffer.putInt(length);

        if (length > 0) {
            buffer.put(data);
        }

        return buffer.array();
    }

    public static HacPacket convertFromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.BIG_ENDIAN);

        if (bytes.length < 16) {
            throw new IllegalArgumentException("Packet too small: " + bytes.length + " bytes.");
        }

        byte version = buffer.get();
        byte type = buffer.get();
        short nodeID = buffer.getShort();
        long timeHigh = buffer.getInt() & 0xFFFFFFFFL;
        long timeLow = buffer.getInt() & 0xFFFFFFFFL;
        long timestamp = (timeHigh << 32) | timeLow;
        int length = buffer.getInt();

        byte[] data = new byte[length];
        if (length > 0) {
            buffer.get(data);
        }

        return new HacPacket(type, nodeID, timestamp, data);
    }

    public String printInfo() {
        return "Protocol{" +
                "version = " + version +
                ", type=" + type +
                ", nodeID=" + nodeID +
                ", timestamp=" + timestamp +
                ", length=" + length +
                ", data=" + (data.length > 0 ? new String(data) : "No Data") +
                '}';
    }

    public byte getVersion() {
        return version;
    }
    public byte getType() {
        return type;
    }
    public short getNodeID() {
        return nodeID;
    }
    public long getTimestamp() {
        return timestamp;
    }
    public int getLength() {
        return length;
    }
    public byte[] getData() {
        return data;
    }




}
