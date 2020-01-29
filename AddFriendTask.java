import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The class AddFriendTask implements the adding of friend by a user. After
 * being added, the friend's nick will be displayed in the user's friend list,
 * represented by the field {@code friends} of the class QuizzleUser.
 * 
 * <p>
 * The class also takes care of checking the legality of the operation returning
 * to the client an error message if the user is trying to add himself as a
 * friend or is trying to add a user whom he is already friend with or the user
 * he wants to add as a friend is not registered.
 */
public class AddFriendTask implements TaskInterface {

    /* ---------------- Fields -------------- */

    /**
     * The database of the QuizzleServer.
     */
    private final QuizzleDatabase database;

    /**
     * The onlineUsers of the QuizzleServer.
     */
    private final ConcurrentHashMap<Integer, String> onlineusers;

    /**
     * The mail depot of the QuizzleServer.
     */
    private final LinkedBlockingQueue<QuizzleMail> depot;

    /**
     * The SelectionKey with attached the Socket upon which to perform the add
     * friend task.
     */
    private final SelectionKey key;

    /**
     * The nickname of the user to add as a friend.
     */
    private final String friend;

    /**
     * Returns a new AddFriendTask.
     * 
     * @param datab   the database.
     * @param onlineu the list of online users.
     * @param queue   the post depot.
     * @param selk    the selection key of interest.
     * @param frnd    the friend's nickname.
     */
    public AddFriendTask(final QuizzleDatabase datab, final ConcurrentHashMap<Integer, String> onlineu,
            final LinkedBlockingQueue<QuizzleMail> queue, final SelectionKey selk, final String frnd) {
        this.database = datab;
        this.onlineusers = onlineu;
        this.depot = queue;
        this.key = selk;
        this.friend = frnd;
    }

    /**
     * Run method.
     */
    public void run() {
        final SocketChannel clientSocket = (SocketChannel) key.channel();
        String msg;
        // Retrieve the nickname from the port number.
        final int clientPort = clientSocket.socket().getPort();
        final String nickname = onlineusers.get(clientPort);

        // Check if the friend is registered.
        if (!(database.retrieveUser(friend) != null)) {
            msg = "Add friend error: user " + friend + " not found.\n";
            TaskInterface.insertMail(depot, key, msg);
            return;
        } else {
            // Then check if the user nickname is not equal to the
            // friend's nickname.
            if (nickname.equals(friend))
                msg = "Add friend error: you cannot add yourself as a friend.\n";
            // Add the friend
            else if (database.addFriend(nickname, friend))
                msg = friend + " is now your friend.\n";
            else
                msg = "Add friend error: you and " + friend + " are already friends.\n";
            // Insert the mail in the post depot.
            TaskInterface.insertMail(depot, key, msg);
            return;
        }
    }
}