import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

/**
 * The TaskInterface provides utility methods for all the tasks.
 */
public interface TaskInterface extends Runnable {

    /**
     * Utility function to send a datagram in a blocking UDP socket.
     * 
     * @param socket the socket.
     * @param msg    the message to insert in the datagram.
     * @param addr   the {@code InetSocketAddress} address.
     */
    public static void sendDatagram(final DatagramSocket socket, final byte[] msg, final InetSocketAddress addr) {
        final DatagramPacket datagram = new DatagramPacket(msg, msg.length, addr);
        try {
            socket.send(datagram);
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Utility function to receive and read a message from a blocking UDP socket.
     * 
     * @param socket the socket.
     * @return the red message.
     * @throws SocketTimeoutException if the socket times out.
     * @throws IOException            if something wrong happens during the call to
     *                                {@code receive}.
     */
    public static String receiveDatagram(final DatagramSocket socket) throws SocketTimeoutException, IOException {
        final byte[] responseBuffer = new byte[16];
        final DatagramPacket response = new DatagramPacket(responseBuffer, responseBuffer.length);
        socket.receive(response);
        final String responseString = new String(response.getData(), response.getOffset(), response.getLength(),
                StandardCharsets.UTF_8);
        return responseString;
    }

    /**
     * Utility function to read a message from a NIO socket.
     * 
     * @param wqClient the socket.
     * @param bBuff    the socket associated byte buffer.
     * @return the message red.
     */
    public static String readMsg(final SocketChannel wqClient, final ByteBuffer bBuff) {
        final byte[] msg = new byte[128];
        int index = 0;
        int k;
        boolean crash = false;
        // Reading the socket.
        try {
            while ((k = wqClient.read(bBuff)) != 0 && !crash) {
                // Client unexpectedly closed the connection.
                if (k == -1) {
                    crash = true;
                }
            }
        } catch (final IOException e) {
            e.printStackTrace();
        }
        // Preparing the buffer for reading form it.
        bBuff.flip();
        // Reading from the buffer.
        while (bBuff.hasRemaining()) {
            msg[index] = bBuff.get();
            index++;
        }
        // Building the raw arguments.
        String rawArgs = "";
        for (final byte b : msg) {
            if ((int) b != 0)
                rawArgs += (char) b;
        }
        // Clearing the buffer, resetting its 'position' to 0 and its 'limit' to its
        // capacity.
        bBuff.clear();
        if (crash)
            return "crashed";
        else
            return rawArgs;
    }

    /**
     * Utility function to write a message in a NIO TCP socket.
     * 
     * @param msg    the message to write
     * @param bBuff  the socket associated byte buffer.
     * @param socket the socket.
     */
    public static void writeMsg(final String msg, final ByteBuffer bBuff, final SocketChannel socket) {
        bBuff.put(msg.getBytes());
        bBuff.flip();
        try {
            while (bBuff.hasRemaining()) {
                socket.write(bBuff);
            }
        } catch (final IOException e) {
            e.printStackTrace();
        }
        bBuff.clear();
    }

    /****** The only public method ******/

    /**
     * The run method.
     */
    public void run();
}