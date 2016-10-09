import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

/* A simple utility class to convert byte arrays to other primitive data types and vice versa */
public class ByteConverter {
    public static final int intByteSize = Integer.SIZE / Byte.SIZE;
    public static final int longByteSize = Long.SIZE / Byte.SIZE;

    public static int byteArrayToInt(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }

    public static long byteArrayToLong(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getLong();
    }

    public static byte[] intToByteArray(int value) {
        return ByteBuffer.allocate(intByteSize).putInt(value).array();
    }

    public static byte[] longToByteArray(long value) {
        return ByteBuffer.allocate(longByteSize).putLong(value).array();
    }

    public static byte[] allocateIntByteArray() {
        return new byte[intByteSize];
    }

    public static byte[] allocateLongByteArray() {
        return new byte[longByteSize];
    }

    public static byte[] intListToByteArray(ArrayList<Integer> integers) {
        byte[] byteArray = new byte[integers.size() * intByteSize];
        for(int i = 0; i < integers.size(); i++) {
            byte[] intAsBytes = intToByteArray(integers.get(i));
            System.arraycopy(intAsBytes, 0, byteArray, i * intByteSize, intByteSize);
        }
        return byteArray;
    }

    public static ArrayList<Integer> byteArrayToIntList(byte[] bytes) {
        ArrayList<Integer> integers = new ArrayList<Integer>();
        for (int i = 0; i < bytes.length; i += intByteSize) {
            byte[] intAsBytes = Arrays.copyOfRange(bytes, i, (i + intByteSize));
            integers.add(ByteConverter.byteArrayToInt(intAsBytes));
        }
        return integers;
    }
}
