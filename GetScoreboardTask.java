import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The class GetScoreboardTask implements the retrieval of the scoreboard by an
 * user. For each user the scoreboard consists in the names and scores of the
 * user's friends sorted by scores in descending order. Upon the execution of
 * this task the WQServer will return the user's scoreboard in the form of a
 * string containing all of the user's friends nicknames, each associated with
 * the corresponding friend's score, and the user's nickname and score.
 */
public class GetScoreboardTask implements Runnable {

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
     * The SelectionKey with attached the Socket upon which to perform the get
     * scoreboard task.
     */
    private final SelectionKey key;

    /**
     * Returns a new GetScoreBoardTask.
     * 
     * @param datab   the database.
     * @param onlineu the list of online users.
     * @param sel     the selector.
     * @param selk    the selection key of interest.
     */
    public GetScoreboardTask(final WQDatabase datab, final ConcurrentHashMap<Integer, String> onlineu,
            final Selector sel, final SelectionKey selk) {
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

        String msg = "";

        // Retrieve the nickname from the port number.
        final int clientPort = clientSocket.socket().getPort();
        final String nickname = onlineUsers.get(clientPort);
        // Build the scoreboard.
        WQUser user = database.retrieveUser(nickname);
        ArrayList<String> friends = user.getFriends();
        ArrayList<WQUser> WQfriends = new ArrayList<WQUser>();
        for (String f : friends)
            WQfriends.add(database.retrieveUser(f));
        WQfriends.add(user);
        WQfriends.sort(null);
        for (WQUser u : WQfriends) {
            msg += u.getNickname() + " " + u.getScore() + " ";
        }
        msg += "\n";
        writeMsg(msg, bBuff, clientSocket);
        key.interestOps(SelectionKey.OP_READ);
        selector.wakeup();
    }
}