import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The class GetFriendListTask implements the retrieval of the friend list by
 * the user. Upon the execution of this task the QuizzleServer will return a
 * string with the contents of the user's friens list which is represented by
 * the field {@code friends} of the class {@link QuizzleUser}.
 * 
 * <p>
 * The task doesn't directly communicate the result of the operation to the
 * client, instead, it inserts in the QuizzleServer post depot a QuizzleMail
 * class' instance that will be delivered to the client by the Mailman thread.
 */
public class GetFriendListTask implements TaskInterface {

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
     * The mail depot of the QuizzleServer.
     */
    private final LinkedBlockingQueue<QuizzleMail> depot;

    /**
     * The SelectionKey with attached the Socket upon which to perform the get
     * friend list task.
     */
    private final SelectionKey key;

    /**
     * Returns a new GetFriendListTask.
     * 
     * @param datab   the database.
     * @param onlineu the list of online users.
     * @param queue   the post depot.
     * @param selk    the selection key of interest.
     */
    public GetFriendListTask(final QuizzleDatabase datab, final ConcurrentHashMap<Integer, String> onlineu,
            final LinkedBlockingQueue<QuizzleMail> queue, final SelectionKey selk) {
        this.database = datab;
        this.onlineUsers = onlineu;
        this.depot = queue;
        this.key = selk;
    }

    /**
     * Run method.
     */
    public void run() {
        final SocketChannel clientSocket = (SocketChannel) key.channel();
        // Retrieve the nickname form the port number.
        final int clientPort = clientSocket.socket().getPort();
        final String nickname = onlineUsers.get(clientPort);
        // Retrieving the user's friend list.
        ArrayList<String> friends = database.retrieveUser(nickname).getFriends();
        String msg;
        // Checking if the user has no friends.
        if (friends.size() != 0)
            msg = "Your friends are: ";
        else
            msg = "You currently have no friends, add some!";
        // Build the friends list.
        for (String f : friends) {
            msg += f + " ";
        }
        msg += "\n";
        // Inserting the results in the post depot.
        TaskInterface.insertMail(depot, key, msg);
    }
}