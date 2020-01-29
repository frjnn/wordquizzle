import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The class GetScoreboardTask implements the retrieval of the scoreboard by an
 * user. For each user the scoreboard consists in the names and scores of the
 * user's friends sorted by scores in descending order. Upon the execution of
 * this task the QuizzleServer will return the user's scoreboard in the form of
 * a string containing all of the user's friends nicknames, each associated with
 * the corresponding friend's score, and the user's nickname and score.
 */
public class GetScoreboardTask implements TaskInterface {

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
     * The mail depot of the code QuizzleServer.
     */
    private final LinkedBlockingQueue<QuizzleMail> depot;

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
     * @param queue   the mail depot.
     * @param selk    the selection key of interest.
     */
    public GetScoreboardTask(final QuizzleDatabase datab, final ConcurrentHashMap<Integer, String> onlineu,
            final LinkedBlockingQueue<QuizzleMail> queue, final SelectionKey selk) {
        this.database = datab;
        this.onlineUsers = onlineu;
        this.depot = queue;
        this.key = selk;
    }

    public void run() {
        final SocketChannel clientSocket = (SocketChannel) key.channel();
        String msg = "";
        // Retrieve the nickname from the port number.
        final int clientPort = clientSocket.socket().getPort();
        final String nickname = onlineUsers.get(clientPort);
        // Build the scoreboard.
        QuizzleUser user = database.retrieveUser(nickname);
        ArrayList<String> friends = user.getFriends();
        ArrayList<QuizzleUser> WQfriends = new ArrayList<QuizzleUser>();
        for (String f : friends)
            WQfriends.add(database.retrieveUser(f));
        WQfriends.add(user);
        WQfriends.sort(null);
        for (QuizzleUser u : WQfriends) {
            msg += u.getNickname() + " " + u.getScore() + " ";
        }
        msg += "\n";
        TaskInterface.insertMail(depot, key, msg);
    }
}