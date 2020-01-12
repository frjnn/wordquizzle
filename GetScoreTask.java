import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The class GetScoreTask implements the retrieval by an user of his score. Upon
 * the execution of this task the WQServer will return a number corresponding to
 * the user's score represented by the field {@code score} of the class WQUser.
 */
public class GetScoreTask implements Runnable {

    /* ---------------- Fields -------------- */

    /**
     * The database of the WQServer.
     */
    private final WQDatabase database;

    /**
     * The onlineUsers of the WQServer.
     */
    private final ConcurrentHashMap<Integer, String> onlineUsers;

    /**
     * The Selector of the code WQServer.
     */
    private final Selector selector;

    /**
     * The SelectionKey with attached the Socket upon which to perform the get score
     * list task.
     */
    private final SelectionKey key;

    /**
     * Returns a new GetScoreTask.
     * 
     * @param datab   the database.
     * @param onlineu the list of online users.
     * @param sel     the selector.
     * @param selk    the selection key of interest.
     */
    public GetScoreTask(final WQDatabase datab, final ConcurrentHashMap<Integer, String> onlineu, final Selector sel,
            final SelectionKey selk) {
        this.database = datab;
        this.onlineUsers = onlineu;
        this.selector = sel;
        this.key = selk;
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
        } catch (final IOException e) {
            e.printStackTrace();
        }
        bBuff.clear();
    }

    public void run() {

        final SocketChannel clientSocket = (SocketChannel) key.channel();
        final ByteBuffer bBuff = (ByteBuffer) key.attachment();

        // Retrieve the nickname form the port number.
        final int clientPort = clientSocket.socket().getPort();
        final String nickname = onlineUsers.get(clientPort);
        String msg = nickname + ", your score is: ";
        // Retrieving the score.
        int score = database.retrieveUser(nickname).getScore();
        msg += score + "\n";
        writeMsg(msg, bBuff, clientSocket);
        key.interestOps(SelectionKey.OP_READ);
        selector.wakeup();
    }
}