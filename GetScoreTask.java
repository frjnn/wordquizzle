import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The class GetScoreTask implements the retrieval by a user of his score. Upon
 * the execution of this task the WQServer will return a number corresponding to
 * the user's score represented by the field {@code score} of the class WQUser.
 */
public class GetScoreTask implements TaskInterface {

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
        TaskInterface.writeMsg(msg, bBuff, clientSocket);
        key.interestOps(SelectionKey.OP_READ);
        selector.wakeup();
    }
}