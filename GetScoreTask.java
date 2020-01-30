import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The class GetScoreTask implements the retrieval by a user of his score. Upon
 * the execution of this task the QuizzleServer will return a number
 * corresponding to the user's score represented by the field {@code score} of
 * the class QuizzleUser.
 * 
 * <p>
 * The task doesn't directly communicate the result of the operation to the
 * client, instead, it inserts in the QuizzleServer post depot a QuizzleMail
 * class' instance that will be delivered to the client by the Mailman thread.
 */
public class GetScoreTask implements TaskInterface {

    /* ---------------- Fields -------------- */

    /**
     * The database of the QuizzleServer.
     */
    private final QuizzleDatabase database;

    /**
     * The onlineUsers of the QuizzleServer.
     */
    private final ConcurrentHashMap<Integer, String> onlineUsers;

    /**
     * The post depot of the code QuizzleServer.
     */
    private final LinkedBlockingQueue<QuizzleMail> depot;

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
     * @param queue   the post depot.
     * @param selk    the selection key of interest.
     */
    public GetScoreTask(final QuizzleDatabase datab, final ConcurrentHashMap<Integer, String> onlineu,
            final LinkedBlockingQueue<QuizzleMail> queue, final SelectionKey selk) {
        this.database = datab;
        this.onlineUsers = onlineu;
        this.depot = queue;
        this.key = selk;
    }

    public void run() {
        final SocketChannel clientSocket = (SocketChannel) key.channel();
        // Retrieve the nickname form the port number.
        final int clientPort = clientSocket.socket().getPort();
        final String nickname = onlineUsers.get(clientPort);
        String msg = nickname + ", your score is: ";
        // Retrieving the score.
        int score = database.retrieveUser(nickname).getScore();
        msg += score + "\n";
        // Inserting the results in the post depot.
        TaskInterface.insertMail(depot, key, msg);
    }
}