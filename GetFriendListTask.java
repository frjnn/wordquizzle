import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The class GetFriendListTask implements the retrieval of the friend list by
 * the user. Upon the execution of this task the WQServer will return a string
 * with the contents of the user's friens list which is represented by the field
 * {@code friends} of the class {@link WQUser}.
 */
public class GetFriendListTask implements TaskInterface {

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
     * The Selector of the WQServer.
     */
    private final Selector selector;

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
     * @param sel     the selector.
     * @param selk    the selection key of interest.
     */
    public GetFriendListTask(final WQDatabase datab, final ConcurrentHashMap<Integer, String> onlineu,
            final Selector sel, final SelectionKey selk) {
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
        ArrayList<String> friends = database.retrieveUser(nickname).getFriends();
        String msg;
        if (friends.size() != 0)
            msg = "Your friends are: ";
        else
            msg = "You currently have no friends, add some!";
        // Build the friends list.
        for (String f : friends) {
            msg += f + " ";
        }
        msg += "\n";
        TaskInterface.writeMsg(msg, bBuff, clientSocket);
        key.interestOps(SelectionKey.OP_READ);
        selector.wakeup();
    }
}