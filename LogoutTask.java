import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The class LogoutTask implements the logout of an user. After being logged
 * out, the user is removed from the WQServer's {@code onlineUsers} field which
 * consists of a {@link ConcurrentHashMap} where the keys are the port numbers
 * on which the users are connected and the values are their nicknames.
 * LogoutTask then proceeds to close the SocketChannel associated with the
 * user's client thus terminating the connection. The class also removes the
 * client's UDP socket InetSocketAddress from the WQServer's field
 * {@code matchBookAddress}.
 * 
 * <p>
 * It's good to note that LogoutTask provides also a brutal logout operation, in
 * the form of a field i.e. {@code brutal} in its costructor. The brutal logout
 * is useful when an user crashes.
 */
public class LogoutTask implements Runnable {

    /* ---------------- Fields -------------- */

    /**
     * The onlineUsers of the WQServer.
     */
    private ConcurrentHashMap<Integer, String> onlineUsers;

    /**
     * The matchAddressBook of the WQServer.
     */
    private ConcurrentHashMap<String, InetSocketAddress> matchBookAddress;

    /**
     * The Selector of the code WQServer.
     */
    private final Selector selector;

    /**
     * The SelectionKey with attached the Socket upon which to perform the logout
     * task.
     */
    private final SelectionKey key;

    /**
     * Whether to perform a brutal logout or not.
     */
    private final boolean brutal;

    /**
     * Returns a new LogoutTask.
     * 
     * @param datab   the database.
     * @param onlineu the list of online users.
     * @param sel     the selector.
     * @param selk    the selection key of interest.
     * @param brutal  if {@code true} performs a brutal logout.
     */
    public LogoutTask(final ConcurrentHashMap<Integer, String> ou,
            final ConcurrentHashMap<String, InetSocketAddress> baddr, final Selector sl, final SelectionKey sk,
            final boolean b) {
        this.onlineUsers = ou;
        this.matchBookAddress = baddr;
        this.selector = sl;
        this.key = sk;
        this.brutal = b;
    }

    /**
     * Utility function to write a message in a NIO TCP socket.
     * 
     * @param msg    the message to write
     * @param bBuff  the socket associated byte buffer.
     * @param socket the socket.
     */
    private void writeMsg(final String msg, final ByteBuffer bBuff, final SocketChannel socket) {
        bBuff.put(msg.getBytes());
        bBuff.flip();
        try {
            while (bBuff.hasRemaining()) {
                socket.write(bBuff);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        bBuff.clear();
    }

    public void run() {

        final SocketChannel clientSocket = (SocketChannel) key.channel();
        final ByteBuffer bBuff = (ByteBuffer) key.attachment();

        // Retrieves the nickname from the port number.
        int clientPort = clientSocket.socket().getPort();
        String nickname = onlineUsers.get(clientPort);

        // Brutally logs out an user.
        if (brutal) {
            if (nickname != null) {
                matchBookAddress.remove(nickname);
                onlineUsers.remove(clientPort);
            }
            try {
                clientSocket.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
            key.cancel();
            selector.wakeup();
            return;
        }

        String msg;

        onlineUsers.remove(clientPort);
        matchBookAddress.remove(nickname);
        System.out.println(nickname + " logged out.\n");
        msg = "Logout successful\n";
        writeMsg(msg, bBuff, clientSocket);
        try {
            clientSocket.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        key.cancel();
        selector.wakeup();
    }
}