import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The MatchTask class implements the match between two users. The
 * implementation is the following: when the {@link QuizzleServer} executes a
 * MatchTask it means that a user challenged one of his friends. After
 * performing all the operation's legality checks of the case, such as returning
 * an error message if the user is trying to match himslef or an offline friend,
 * MatchTask initialize the {@code matchSelector} used to read the users'
 * translation from their match sockets and sends an UDP invitation message to
 * the challenged user containing the nickname of the challenging user and a TCP
 * port, i.e. the {@code matchChannel}'s socket port, that, if he accepts the
 * invitation, must be used to join the match. The invitation time to live is
 * implemented by setting a timeout on the {@code invSocket}, which is the
 * socket used by the task to send the match invitation and to receive the
 * response. If the challenged user accept, the match port is communicated to
 * the challengig user via its main TCP connection with the QuizzleServer.
 * MatchTask then waits for both users to join the match. When both users join
 * the match begins. The words are randomly selected from the
 * {@code ItalianDictionary.txt} file and translated. The match takes place only
 * if the translation service is available, otherwise the server returns to both
 * the players an error message. When the match ends the final score of both
 * user is computed and assigned, immediatly after, the QuizzleServer's
 * {@link QuizzleDatabase} instance is serialized to save the changes.
 * 
 * <p>
 * If a user correctly translates a word he is assigned 2 points, if he doesn't
 * he is assigned a score of -1 and if he skips the word he is assigned 0
 * points.
 * 
 * <p>
 * When the match times out or both users have made an attempt in translating
 * all of the words the scores are assigned. If the match times out the
 * remaining translations sent by the users are discarded and 0 is assigned for
 * every missing translation to both users score. The match bonus assigned to
 * the winner consists in 3 points.
 */
public class MatchTask implements TaskInterface {

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
     * The matchBookAddress of the QuizzleServer.
     */
    private final ConcurrentHashMap<String, InetSocketAddress> matchBookAddress;

    /**
     * The Selector of the code QuizzleServer.
     */
    private final Selector selector;

    /**
     * The SelectionKey with attached the Socket associated with the challenger's
     * client.
     */
    private final SelectionKey key;

    /**
     * The nickname of the the friend that the user wants to challenge.
     */
    private final String friend;

    /**
     * The match duration in minutes. It's an int stating how many minutes a match
     * shall last. It's specified by command line upon server's creation.
     */
    private final int matchTimer;

    /**
     * The match invitation time to live. It's an int stating how many seconds a
     * match invitation shall remain valid. It's specified by command line upon
     * server's creation.
     */
    private final int acceptTimer;

    /**
     * The number of words used during the match. It's specified by command line
     * upon server's creation.
     */
    private final int matchWords;

    /**
     * Returns a new MatchTask.
     * 
     * @param datab   the database.
     * @param onlineu the list of online users.
     * @param maddr   the list of online users UDP addresses.
     * @param sel     the selector.
     * @param selk    the selection key of interest.
     * @param frnd    the friend to challenge.
     * @param n       the match legnth.
     * @param m       the invite expiry.
     * @param l       the number of words.
     */
    public MatchTask(final QuizzleDatabase datab, final ConcurrentHashMap<Integer, String> onlineu,
            final ConcurrentHashMap<String, InetSocketAddress> maddr, final Selector sel, final SelectionKey selk,
            final String frnd, final int n, final int m, final int l) {
        this.database = datab;
        this.onlineUsers = onlineu;
        this.matchBookAddress = maddr;
        this.selector = sel;
        this.key = selk;
        this.friend = frnd;
        this.matchTimer = n;
        this.acceptTimer = m;
        this.matchWords = l;
    }

    public void run() {

        final SocketChannel clientSocket = (SocketChannel) key.channel();
        final ByteBuffer bBuff = (ByteBuffer) key.attachment();

        // This is the variable where the response to be sent to the challenging client
        // is stored.
        String msg;
        // Will be set to false if the mymemory service is not reachable.
        boolean available = true;
        // Those booleans are used to terminate the task if the service is unavailable.
        boolean term1 = false;
        boolean term2 = false;

        // Retrieve the user's nickname from the port number.
        final int clientPort = clientSocket.socket().getPort();
        final String nickname = onlineUsers.get(clientPort);

        // Then check if the user nickname is not equal to the
        // friend's nickname.
        if (nickname.equals(friend)) {
            msg = "Match error: you cannot challenge yourself.\n";
            TaskInterface.writeMsg(msg, bBuff, clientSocket);
            key.interestOps(SelectionKey.OP_READ);
            selector.wakeup();
            return;
        } else {
            // Check if the two users are friends.
            final QuizzleUser challenger = database.retrieveUser(nickname);
            final ArrayList<String> challengerFriends = challenger.getFriends();
            if (!(challengerFriends.contains(friend))) {
                msg = "Match error: user " + friend + " and you are not friends.\n";
                TaskInterface.writeMsg(msg, bBuff, clientSocket);
                key.interestOps(SelectionKey.OP_READ);
                selector.wakeup();
                return;
            } else {
                // Check if the friend is offline.
                if (!onlineUsers.containsValue(friend)) {
                    msg = "Match error: " + friend + " is offline\n";
                    TaskInterface.writeMsg(msg, bBuff, clientSocket);
                    key.interestOps(SelectionKey.OP_READ);
                    selector.wakeup();
                    return;
                } else {
                    // Start the invitation login.
                    // Setting up the UDP socket from which send invitations.
                    DatagramSocket invSocket = null;
                    try {
                        invSocket = new DatagramSocket();
                        invSocket.setSoTimeout(acceptTimer * 1000);
                    } catch (final SocketException e) {
                        e.printStackTrace();
                    }
                    // Initializing challenge selector.
                    Selector matchSelector = null;
                    ServerSocketChannel matchChannel = null;
                    try {
                        matchSelector = Selector.open();
                        matchChannel = ServerSocketChannel.open();
                        final ServerSocket matchSocket = matchChannel.socket();
                        // Listening on an ephemeral port for the two players connections.
                        final InetSocketAddress address = new InetSocketAddress(0);
                        matchSocket.bind(address);
                        matchChannel.configureBlocking(false);
                        // Registering the matchChannel for the accept operation.
                        final SelectionKey acceptionKey = matchChannel.register(matchSelector, SelectionKey.OP_ACCEPT);
                    } catch (final IOException IOE) {
                        IOE.printStackTrace();
                    }
                    // The invitation consists in the nickname of the challenger user and the port
                    // number to which the challenged user must connect if he accepts the
                    // invitation.
                    final byte[] invitation = (nickname + "/" + matchChannel.socket().getLocalPort()).getBytes();
                    // Getting the challenged user's IP address from the matchBookAddress in order
                    // to set the packet
                    // destination.
                    final InetSocketAddress friendAddress = matchBookAddress.get(friend);
                    // Send the invitation.
                    TaskInterface.sendDatagram(invSocket, invitation, friendAddress);
                    // Receive invitation.
                    String response = null;
                    try {
                        response = TaskInterface.receiveDatagram(invSocket);
                    } catch (final SocketTimeoutException e) {
                        msg = "Match error: invitation to " + friend + " timed out.\n";
                        // If the invitaiton times out, notifies the friend to delete the pending match
                        // invitation from his client's challengers hashmap by sending another UDP
                        // datagram. Doing so ensures that the MatchListener thread of the challenged
                        // client knows to throw away the expired invitation.
                        byte[] friendMsg = ("TIMEOUT/" + nickname).getBytes();
                        TaskInterface.sendDatagram(invSocket, friendMsg, friendAddress);
                        TaskInterface.writeMsg(msg, bBuff, clientSocket);
                        key.interestOps(SelectionKey.OP_READ);
                        selector.wakeup();
                        return;
                    } catch (final IOException e) {
                        e.printStackTrace();
                    }
                    // Analyzing the response, the eventual refusal of the friend must be
                    // communicated to the challeging client.
                    // If the friends refuses must notify the challenging user.
                    if (response.equals("N")) {
                        msg = friend + " refused your match invitation.\n";
                        TaskInterface.writeMsg(msg, bBuff, clientSocket);
                        key.interestOps(SelectionKey.OP_READ);
                        selector.wakeup();
                        return;
                    } else if (response.equals("Y")) {
                        // The friends accepted the challenge must communicate to the challenging user
                        // the match socket port and prepare the words.
                        msg = friend + " accepted your match invitation./" + matchChannel.socket().getLocalPort()
                                + "\n";
                        TaskInterface.writeMsg(msg, bBuff, clientSocket);
                        boolean joined1 = false; // Challenging user joined status.
                        boolean joined2 = false; // Challenged user joined status.
                        SocketChannel results1 = null; // Challenging user final score.
                        ByteBuffer resultsBuff1 = null; // Challening user's buffer to write the final results into.
                        SocketChannel results2 = null; // Challenged user final score.
                        ByteBuffer resultsBuff2 = null; // Challenged user user's buffer to write the final results
                                                        // into.
                        int challengedPort = 0; // Keeping trace of the challenged user port to distinguish the socket
                                                // channels during the selection operation.
                        // Getting the players addresses from the matchBookAddress.
                        InetAddress add1 = matchBookAddress.get(nickname).getAddress();
                        InetAddress add2 = matchBookAddress.get(friend).getAddress();
                        // Waiting for both players to join the match.
                        while (!joined1 || !joined2) {
                            try {
                                final int readyKeys = matchSelector.select();
                                if (readyKeys > 0) {
                                    final Set<SelectionKey> keys = matchSelector.selectedKeys();
                                    final Iterator<SelectionKey> keysIterator = keys.iterator();
                                    while (keysIterator.hasNext()) {
                                        // Extract one key
                                        final SelectionKey key = keysIterator.next();
                                        // The key must be manually removed from the iterator, the selector doesn't
                                        // Automatically remove the istances of the SelectionKeys.
                                        keysIterator.remove();
                                        if (key.isAcceptable()) {
                                            try {
                                                // Accepting the connection from the client
                                                final ServerSocketChannel channel = (ServerSocketChannel) key.channel();
                                                final SocketChannel client = channel.accept();
                                                final InetAddress addr = client.socket().getInetAddress();
                                                final ByteBuffer clientBuff = ByteBuffer.allocate(256);
                                                // Checking the addresses.
                                                if (addr.equals(add1) && !joined1) {
                                                    results1 = client;
                                                    joined1 = true;
                                                    resultsBuff1 = clientBuff;
                                                } else if (addr.equals(add2) && !joined2) {
                                                    results2 = client;
                                                    challengedPort = client.socket().getPort();
                                                    joined2 = true;
                                                    resultsBuff2 = clientBuff;
                                                }
                                                client.configureBlocking(false);
                                                // Registering the client socket for read operations.
                                                final SelectionKey clientKey = client.register(matchSelector,
                                                        SelectionKey.OP_READ);
                                                clientKey.attach(clientBuff);
                                            } catch (final IOException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }
                                }

                            } catch (final IOException IOE) {
                                IOE.printStackTrace();
                            }
                        }
                        // Retrieving the words and the corresponding translations.
                        HashMap<String, ArrayList<String>> dictionary = null;
                        try {
                            dictionary = new MatchWords(matchWords).requestWords();
                        } catch (final IOException e) {
                            // If the translation service is not available.
                            available = false;
                            // e.printStackTrace();
                        }
                        // Getting the dictionay map keys, i.e. the words.
                        String[] words;
                        if (available) {
                            words = dictionary.keySet().toArray(new String[dictionary.size()]);
                        } else {
                            words = null;
                        }
                        // Setting the match timer.
                        final long startTime = System.currentTimeMillis();
                        long currentTime = System.currentTimeMillis();
                        // Seting the array where the two player translation will be stored.
                        final String[] response1 = new String[matchWords];
                        final String[] response2 = new String[matchWords];
                        // Indexes, will be used in the algorith to calculate the scores.
                        int index1 = 0;
                        int index2 = 0;
                        // Match cycle, the loop stops when either:
                        // - the time runs out.
                        // - both players send all of the translations.
                        // - both players have been notified that the service is unavailable, if the
                        // service is unavailable.
                        while (currentTime < (startTime + (matchTimer * 60000))
                                && (index1 < matchWords + 1 || index2 < matchWords + 1) && (!term1 || !term2)) {
                            try {
                                final int readyKeys = matchSelector.selectNow();
                                if (readyKeys > 0) {
                                    final Set<SelectionKey> keys = matchSelector.selectedKeys();
                                    final Iterator<SelectionKey> keysIterator = keys.iterator();
                                    while (keysIterator.hasNext()) {
                                        // Extract one key.
                                        final SelectionKey key = keysIterator.next();
                                        // The key must be manually removed from the iterator, the selector doesn't
                                        // automatically remove the istances of the SelectionKeys.
                                        keysIterator.remove();
                                        if (key.isReadable()) {
                                            // Getting the translation submitted by the player.
                                            final SocketChannel clientChann = (SocketChannel) key.channel();
                                            final ByteBuffer clientBuff = (ByteBuffer) key.attachment();
                                            final String translation = TaskInterface.readMsg(clientChann, clientBuff);
                                            // If a user crashes setting to blank all of his remaining responses.
                                            if (translation.equals("crashed")) {
                                                if (clientChann.socket().getPort() == challengedPort) {
                                                    for (int i = index2 - 1; i < matchWords; i++) {
                                                        response2[i] = "";
                                                    }
                                                    index2 = matchWords + 1;
                                                } else {
                                                    for (int i = index1 - 1; i < matchWords; i++) {
                                                        response1[i] = "";
                                                    }
                                                    index1 = matchWords + 1;
                                                }
                                                key.interestOps(0);
                                            } else {
                                                if (!available) {
                                                    // The mymemoryAPI is down.
                                                    TaskInterface.writeMsg(
                                                            "Sorry, the translation service is unavailable. Try later."
                                                                    + "\n",
                                                            clientBuff, clientChann);
                                                    if (clientChann.socket().getPort() == challengedPort) {
                                                        term1 = true;
                                                    } else {
                                                        term2 = true;
                                                    }
                                                } else {
                                                    // Submitting a word and updating the indexes.
                                                    final String[] split = translation.split("/");
                                                    final String name = split[1];
                                                    // In order to distinguish between the two players a protocol has
                                                    // been adopted. This protocol imposes that the translations must be
                                                    // followed by the nickname of the client that sen them.
                                                    if (translation.equals("START/" + friend)) {
                                                        TaskInterface.writeMsg(words[index2] + "\n", clientBuff,
                                                                clientChann);
                                                        index2++;
                                                    } else if (translation.equals("START/" + nickname)) {
                                                        TaskInterface.writeMsg(words[index1] + "\n", clientBuff,
                                                                clientChann);
                                                        index1++;
                                                    } else {
                                                        if (name.equals(friend)) {
                                                            response2[index2 - 1] = split[0];
                                                            if (index2 < matchWords) {
                                                                TaskInterface.writeMsg(words[index2] + "\n", clientBuff,
                                                                        clientChann);
                                                            }
                                                            index2++;
                                                        } else if (name.equals(nickname)) {
                                                            response1[index1 - 1] = split[0];
                                                            if (index1 < matchWords) {
                                                                TaskInterface.writeMsg(words[index1] + "\n", clientBuff,
                                                                        clientChann);
                                                            }
                                                            index1++;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (final IOException e) {
                                e.printStackTrace();
                            }
                            // Updating the time.
                            currentTime = System.currentTimeMillis();
                        }
                        if (available) {
                            // Computing the scores in an old fashioned way.
                            int score1 = 0;
                            int score2 = 0;
                            final int bonus = 3;
                            String msg1 = "";
                            String msg2 = "";
                            for (int i = 0; i < index1 - 1; i++) {
                                final ArrayList<String> translation = dictionary.get(words[i]);
                                if (translation.contains(response1[i])) {
                                    score1 += 2;
                                } else if (response1[i].equals(""))
                                    score1 += 0;
                                else
                                    score1 -= 1;
                            }
                            for (int i = 0; i < index2 - 1; i++) {
                                final ArrayList<String> translation = dictionary.get(words[i]);
                                if (translation.contains(response2[i])) {
                                    score2 += 2;
                                } else if (response2[i].equals(""))
                                    score2 += 0;
                                else
                                    score2 -= 1;
                            }
                            // Assigning the match bonus to the winner, if there's one.
                            if (score1 < score2) {
                                score2 += bonus;
                                msg2 = "won";
                                msg1 = "lost";
                            } else if (score2 < score1) {
                                score1 += bonus;
                                msg2 = "lost";
                                msg1 = "won";
                            } else
                                msg1 = msg2 = "drew";
                            // Building the results messages.
                            if (currentTime < (startTime + (matchTimer * 60000))) {
                                TaskInterface.writeMsg(
                                        "END/You have scored: " + score1 + " points. You " + msg1 + ".\n", resultsBuff1,
                                        results1);
                                TaskInterface.writeMsg(
                                        "END/You have scored: " + score2 + " points. You " + msg2 + ".\n", resultsBuff2,
                                        results2);
                            } else {
                                TaskInterface.writeMsg(
                                        "END/Time out: you have scored: " + score1 + " points. You " + msg1 + ".\n",
                                        resultsBuff1, results1);
                                TaskInterface.writeMsg(
                                        "END/Time out: you have scored: " + score2 + " points. You " + msg2 + "\n",
                                        resultsBuff2, results2);
                            }
                            // Setting the scores in the database.
                            database.setScore(nickname, score1);
                            database.setScore(friend, score2);
                        }
                        key.interestOps(SelectionKey.OP_READ);
                        selector.wakeup();
                        return;
                    }
                }
            }
        }
    }
}