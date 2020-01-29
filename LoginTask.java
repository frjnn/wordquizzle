import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The class LoginTask implements the login of a user. After being logged in,
 * the user is inserted in the QuizzleServer's {@code onlineUsers} field which
 * consists of a {@link ConcurrentHashMap} where the keys are the port numbers
 * on which the users are connected to and the values are their nicknames. It
 * also adds the user's client UDP socket address to the QuizzleServer's
 * {@code matchAddressBook} field.
 * 
 * 
 * <p>
 * The class also takes care of checking the legality of the operation returning
 * to the client a error if the user is already logged or if the client is
 * trying to request multiple logins.
 */
public class LoginTask implements TaskInterface {

    /* ---------------- Fields -------------- */

    /**
     * The database of the QuizzleServer.
     */
    private final QuizzleDatabase database;

    /**
     * The onlineUsers of the QuizzleServer.
     */
    private ConcurrentHashMap<Integer, String> onlineUsers;

    /**
     * The matchAddressBook of the QuizzleServer.
     */
    private ConcurrentHashMap<String, InetSocketAddress> matchAddressBook;

    /**
     * The server's post depot.
     */
    private final LinkedBlockingQueue<QuizzleMail> depot;
    /**
     * The SelectionKey with attached the Socket upon which to perform the login
     * task.
     */
    private final SelectionKey key;

    /**
     * The nickname of the user that wants to be logged in.
     */
    private final String nickname;

    /**
     * The password of the user that wants to be logged in.
     */
    private final String password;

    /**
     * The client's UDP port that will be used to send UDP match invitations.
     */
    private final int UDP_port;

    /**
     * Returns a new LoginTask.
     * 
     * @param datab   the database.
     * @param onlineu the list of online users.
     * @param baddr   the UDP book address.
     * @param queue   the post depot.
     * @param selk    the selection key of interest.
     * @param nick    the user's nickname.
     * @param pwd     the user's password.
     * @param prt     the user's port to use for UDP communication.
     */
    public LoginTask(final QuizzleDatabase datab, final ConcurrentHashMap<Integer, String> onlineu,
            final ConcurrentHashMap<String, InetSocketAddress> baddr, final LinkedBlockingQueue<QuizzleMail> queue,
            final SelectionKey selk, final String nick, final String pwd, final int prt) {
        this.database = datab;
        this.onlineUsers = onlineu;
        this.matchAddressBook = baddr;
        this.depot = queue;
        this.key = selk;
        this.nickname = nick;
        this.password = pwd;
        this.UDP_port = prt;
    }

    public void run() {
        final SocketChannel clientSocket = (SocketChannel) key.channel();
        String msg;
        // Check if user is registered.
        if (!(database.retrieveUser(nickname) != null)) {
            msg = "Login error: user " + nickname + " not found. Please register.\n";
            TaskInterface.insertMail(depot, key, msg);
            return;
        } else {
            // Check if user is already logged with another account.
            Integer clientPort = clientSocket.socket().getPort();
            boolean alreadyLogged = onlineUsers.containsValue(nickname);
            boolean loggingAnotherAccount = onlineUsers.containsKey(clientPort); // A port is already associated with
                                                                                 // the client.
            if (alreadyLogged || loggingAnotherAccount) {
                if (alreadyLogged)
                    msg = "Login error: " + nickname + " is already logged in.\n";
                else
                    msg = "Login error: you are already logged with another account.\n";
                TaskInterface.insertMail(depot, key, msg);
                return;
            } else {
                // If user is not logged then must check the password.
                final int hash = password.hashCode();
                QuizzleUser usr = database.retrieveUser(nickname);
                if (hash == usr.getPwdHash()) {
                    // If the password matches then proceeds inserting the user among the online
                    // users and the user's IP in the match address book.
                    InetAddress addr = clientSocket.socket().getInetAddress();
                    InetSocketAddress socketAddr = new InetSocketAddress(addr, UDP_port);
                    onlineUsers.put(clientPort, nickname);
                    matchAddressBook.put(nickname, socketAddr);
                    System.out.println(nickname + " logged in.\n");
                    msg = "Login successful.\n";
                    TaskInterface.insertMail(depot, key, msg);
                } else {
                    // If the password doesn't match returns an error message.
                    msg = "Login error: wrong password.\n";
                    TaskInterface.insertMail(depot, key, msg);
                    return;
                }
            }
        }
    }
}