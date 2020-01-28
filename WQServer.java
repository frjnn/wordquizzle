import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * WQServer is the core class of the Word Quizzle application. The registration
 * of new users is handled by providing a remote method {@code registerUser}.
 * Newly registered user are then inserted in the server's database which is an
 * instance of the class {@link WQDatabase} thus providing persistence of the
 * users datas.
 * 
 * <p>
 * The WQServer class is also provided with a threadpool {@code tPool} and a
 * selector {@code selector} in order to spawn a limited number of threads thus
 * avoiding excessive load on the cpu and minimizing the overhead due to
 * numerous context changes occurring by assigning one thread per connection.
 * The threadpool is then fed tasks by the selector, whose job is solely to
 * accept new connections and to read the clients requests.
 * 
 * <p>
 * To keep track of the users who are logged in WQServer has a field
 * {@code onlineUsers} of type {@link ConcurrentHashMap} where the keys are the
 * port numbers on which the users clients are connected and the values are
 * their nicknames.
 * 
 * <p>
 * WQServer is able to function on the local area network of the host on which
 * the program is being run. In order to achieve this the class is provided with
 * a {@link DatagramChannel} registered on the class selector {@code selector}.
 * WQServer responds to the clients probe UDP packets with its IP address and
 * its TCP port number.
 * 
 * <p>
 * WQServer provides an highly scalable architecture by combining an iterative
 * task dispatching with a concurrent task execution.
 * 
 * <p>
 * WQServer is able to operate with both command line WQClients and graphic user
 * interface client simultaneously.
 */
public class WQServer extends UnicastRemoteObject implements WQRegistrationRMI {

    /* ---------------- Fields -------------- */

    /**
     * The class serial version UID.
     */
    private static final long serialVersionUID = 1;

    /**
     * The WQDatabase. Initialized upon server creation. It first tries to
     * deserialize, if exists, the Database.json file and, if it doesn't, it creates
     * it.
     */
    private final WQDatabase database;

    /**
     * The threadpool. Initialized upon server creation. The number of threads is
     * currently hardcoded to four as most cpus nowadays have two cores with four
     * threads.
     */
    private final ThreadPoolExecutor threadpool;

    /**
     * The selector. Initialized upon server creation.
     */
    private Selector selector;

    /**
     * The channel for the server's TCP socket on which it accepts new connection
     * requests. Initialized upon server creation.
     */
    private ServerSocketChannel TCP_channel;

    /**
     * The channel for the server's UDP socket on which it communicates the
     * InetSocketAddress of TCPsocket to the clients sending probing UDP datagrams.
     */
    private DatagramChannel probeChannel;

    /**
     * The port on which the server listens for connections. Can be specified by
     * command line.
     */
    private final int TCP_port;

    /**
     * The port on which the server listens for clients probe UDP datagrams. Can be
     * specified by command line.
     */
    private final int UDP_port;

    /**
     * The data structure used by WQServer to keep trace of online users. For every
     * logged user u the key is the server port number on which the connection with
     * the client of u is taking place and the value is u's nickname. It's also used
     * to check if the requested operation is legal during task execution.
     */
    private final ConcurrentHashMap<Integer, String> onlineUsers;

    /**
     * The data structure used by WQServer to keep trace of online users IP
     * addresses that will be used for UDP communication during match invitation.
     * For every logged user u the key is the u's nickname and the value is u's
     * datagram socket's address. It's used to send match invitations.
     */
    private final ConcurrentHashMap<String, InetSocketAddress> matchAddressBook;

    /**
     * The match duration in minutes. It's an int stating how many minutes a match
     * shall last. It's specified by command line.
     */
    private final int matchDuration;

    /**
     * The match invitation time to live. It's an int stating how many seconds a
     * match invitation shall remain valid. It's specified by command line.
     */
    private final int acceptDuration;

    /**
     * The number of words to be displayed during a match. Is an int stating how
     * many english words the server shall provide to the players. It's specified by
     * command line.
     */
    private final int numWords;

    /**
     * The maximum number of threads in the threadpool, an hihg number would be
     * useful for executing multiple MatchTasks at the same time.
     */
    private final int threads;

    /* ---------------- Public operations -------------- */

    /**
     * Creates a new WQServer and initializes the dayabase, the threadpool, the
     * selector, the server's TCP and UDP socket channels plus all of the other
     * class fields.
     * 
     * @param port       the server's TCP port number.
     * @param probe      the server's UDP probe port.
     * @param length     the match duration in minutes.
     * @param invitation the espiry time of a match invitation in seconds.
     * @param words      the number of words to be provided for tradution during a
     *                   match.
     * @param thr        the maxPoolSize of the ThreadPoolExecutor.
     * @throws RemoteException could be be thrown, WQServer is a remote object.
     */
    public WQServer(final int port, final int probe, final int length, final int invitation, final int words,
            final int thr) throws RemoteException {
        super();
        this.database = new WQDatabase();
        this.threads = thr;
        this.threadpool = new ThreadPoolExecutor(4, threads, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        try {
            this.selector = Selector.open();
            this.TCP_channel = ServerSocketChannel.open();
            this.probeChannel = DatagramChannel.open();
        } catch (final IOException IOE) {
            IOE.printStackTrace();
        }
        this.TCP_port = port;
        this.UDP_port = probe;
        this.onlineUsers = new ConcurrentHashMap<Integer, String>();
        this.matchAddressBook = new ConcurrentHashMap<String, InetSocketAddress>();
        this.matchDuration = length;
        this.acceptDuration = invitation;
        this.numWords = words;
    }

    /**
     * User registration by remote method invocation. Takes the user's nickname and
     * the user's password, if the password is empty or the nickname is already
     * taken returns an error message otherwise proceeds to insert the user in the
     * database and the calls {@code serialize()} on the server's WQDatabase
     * instance.
     * 
     * @param username the user's username.
     * @param password the user's password.
     */
    @Override
    public String registerUser(final String username, final String password) {
        if (username == null) {
            System.out.println("Registration error: Invalid username.");
            return "Invalid username.";
        }
        if (password == null) {
            System.out.println("Registration error: Invalid password.");
            return "Invalid password.";
        }
        if (this.database.insertUser(username, password) == true) {
            System.out.println("Registration succeeded.");
            return "Registration succeeded.";
        } else {
            System.out.println("Registration error: Nickname already taken.");
            return "Nickname already taken.";
        }
    }

    /**
     * Main method. This is where the logic takes place.
     * 
     * @param args main args.
     * @throws RemoteException a remote exception may occur during the execution of
     *                         {@code rebind}.
     */
    public static void main(final String[] args) throws RemoteException {

        // Checking main args.
        if (args.length != 6 || args.length != 0 && args[0].equals("--help")) {
            System.out.println(
                    "Correct usage:\n\njava WQServer <TCP_port> <UDP_port> <match_timer> <invitation_timer> <num_words> <num_threads>");
            System.exit(1);
        }

        // Parsing main args.
        final int tcp_port = Integer.parseInt(args[0]);
        final int udp_port = Integer.parseInt(args[1]);
        final int match_timer = Integer.parseInt(args[2]);
        final int invitation_timer = Integer.parseInt(args[3]);
        final int num_words = Integer.parseInt(args[4]);
        final int threads = Integer.parseInt(args[5]);

        if (tcp_port <= 1024 || udp_port <= 1024) {
            System.out.println("Please use ephemeral port numbers.");
            System.exit(1);
        }

        if (match_timer <= 0 || invitation_timer <= 0) {
            System.out.println("Timers can only be set to positive values.");
            System.exit(1);
        }

        if (num_words <= 0) {
            System.out.println("Please enter a valid number of words.");
            System.exit(1);
        }

        if (threads < 4) {
            System.out.println("The maximum pool size must be at least 4.");
            System.exit(1);
        }

        // Server creation.
        final WQServer server = new WQServer(tcp_port, udp_port, match_timer, invitation_timer, num_words, threads);

        // Remote method registration.
        LocateRegistry.createRegistry(5678);
        final Registry r = LocateRegistry.getRegistry(5678);
        r.rebind("REGISTRATION", server);

        // Setting up the Selector for accepting new connections.
        try {
            final ServerSocket TCPsock = server.TCP_channel.socket();
            final DatagramSocket UDPsock = server.probeChannel.socket();
            final InetSocketAddress TCPaddress = new InetSocketAddress(server.TCP_port);
            final InetSocketAddress UDPAddress = new InetSocketAddress(server.UDP_port);
            TCPsock.bind(TCPaddress);
            UDPsock.bind(UDPAddress);
            server.TCP_channel.configureBlocking(false);
            server.probeChannel.configureBlocking(false);
            final SelectionKey TCPkey = server.TCP_channel.register(server.selector, SelectionKey.OP_ACCEPT);
            final SelectionKey UDPkey = server.probeChannel.register(server.selector, SelectionKey.OP_READ);
            System.out.println("Listening for connections on port " + server.TCP_port + " of host "
                    + InetAddress.getLocalHost().getHostName() + ".");
        } catch (final IOException IOE) {
            IOE.printStackTrace();
        }

        // Server loop.
        while (true) {

            try {
                // The number of keys upon which an accept connection operation or a read
                // operation can be performed.
                final int readyKeys = server.selector.select();
                if (readyKeys > 0) {
                    // The set containing those keys, and an iterator over this set.
                    final Set<SelectionKey> keys = server.selector.selectedKeys();
                    final Iterator<SelectionKey> keysIterator = keys.iterator();
                    // Selector loop: here we check if an accept connection operation or a read
                    // operation can be performed upon the keys in the ready set.
                    // The only key registered for the accept operation
                    // is the one associated with the server's socket and simmetrically
                    // the keys registered for the read opearation are the ones associated with the
                    // clients sockets and the server udp socket for clients probing.
                    while (keysIterator.hasNext()) {
                        // Extracts one key.
                        final SelectionKey key = keysIterator.next();
                        // The key must be manually removed from the iterator, the selector doesn't
                        // automatically remove the istances of the SelectionKeys.
                        keysIterator.remove();
                        try {

                            if (key.isAcceptable()) {
                                // Accepting the connection from the client.
                                final ServerSocketChannel wqServer = (ServerSocketChannel) key.channel();
                                final SocketChannel wqClient = wqServer.accept();
                                System.out.println(
                                        "Accepted connection from client: " + server.getClientHostname(wqClient));
                                wqClient.configureBlocking(false);
                                // Registering the client's socket for read operations.
                                final SelectionKey keyClient = wqClient.register(server.selector, SelectionKey.OP_READ);
                                final ByteBuffer bBuff = ByteBuffer.allocate(512);
                                keyClient.attach(bBuff);

                            } else if (key.isReadable()) {
                                // Getting the channel.
                                final Channel channel = key.channel();
                                // Testing whether the channel is a DatagramChannel or a SocketChannel.
                                if (channel instanceof DatagramChannel) {
                                    // If it's a DatagramChannel must respond to the probe wih the server's tcp
                                    // socket port.
                                    final DatagramChannel wqProbeChannel = (DatagramChannel) key.channel();
                                    final ByteBuffer probeBuffer = ByteBuffer.allocate(28); // The dimension of an empty
                                                                                            // UDP
                                    // datagram.
                                    final SocketAddress probeAddress = wqProbeChannel.receive(probeBuffer);
                                    final String msg = String.valueOf(server.TCP_port);
                                    try {
                                        final int n = wqProbeChannel.send(ByteBuffer.wrap(msg.getBytes()),
                                                probeAddress);
                                    } catch (final IOException e) {
                                        e.printStackTrace();
                                    }
                                } else {
                                    // Reading the TCP socket associated with the key, the key's interest is
                                    // manually zeroed to and will be set again to the read operation by the tasks
                                    // after their completion.
                                    key.interestOps(0);

                                    // This boolean is set to true if the user crashses, in fact, if that happens,
                                    // the key associated with the client's socket will be readable and '-1'
                                    // will be read from it.
                                    boolean crash = false;

                                    final SocketChannel wqClient = (SocketChannel) key.channel();
                                    final ByteBuffer bBuff = (ByteBuffer) key.attachment();
                                    final byte[] msg = new byte[128];
                                    int index = 0;
                                    int k;
                                    // Reading the socket.
                                    while ((k = wqClient.read(bBuff)) != 0 && !crash) {
                                        // Client unexpectedly closed the connection.
                                        if (k == -1) {
                                            // If the client crashed a brutal logout is performed.
                                            crash = true;
                                            server.threadpool.execute(new LogoutTask(server.onlineUsers,
                                                    server.matchAddressBook, server.selector, key, true));
                                        }
                                    }
                                    if (crash)
                                        continue;
                                    // Preparing the buffer for reading form it.
                                    bBuff.flip();
                                    // Reading from the buffer.
                                    while (bBuff.hasRemaining()) {
                                        msg[index] = bBuff.get();
                                        index++;
                                    }
                                    // Building the raw arguments to pass to tasks.
                                    String rawArgs = "";
                                    for (final byte b : msg) {
                                        if ((int) b != 0)
                                            rawArgs += (char) b;
                                    }
                                    // Clearing the buffer, resetting its 'position' to 0 and its 'limit' to its
                                    // capacity.
                                    bBuff.clear();
                                    // Splitting the raw arguments, obtaining the processed arguments.
                                    final String[] procArgs = rawArgs.split(" ");
                                    // codo_op is an integer indicating which operation the client requested.
                                    final Integer cod_op = Integer.parseInt(procArgs[0]);

                                    switch (cod_op) {

                                    case 0:
                                        // Login Operation.
                                        final String nick = procArgs[1];
                                        final String pwd = procArgs[2];
                                        final int port = Integer.parseInt(procArgs[3]);
                                        final LoginTask logtsk = new LoginTask(server.database, server.onlineUsers,
                                                server.matchAddressBook, server.selector, key, nick, pwd, port);
                                        server.threadpool.execute(logtsk);
                                        break;
                                    case 1:
                                        // Logout Operation.
                                        final LogoutTask unlogtsk = new LogoutTask(server.onlineUsers,
                                                server.matchAddressBook, server.selector, key, false);
                                        server.threadpool.execute(unlogtsk);
                                        break;
                                    case 2:
                                        // Add Friend Operation.
                                        final String friend = procArgs[1];
                                        final AddFriendTask addtsk = new AddFriendTask(server.database,
                                                server.onlineUsers, server.selector, key, friend);
                                        server.threadpool.execute(addtsk);
                                        break;
                                    case 3:
                                        // Get Friends List Operation.
                                        final GetFriendListTask lsttsk = new GetFriendListTask(server.database,
                                                server.onlineUsers, server.selector, key);
                                        server.threadpool.execute(lsttsk);
                                        break;
                                    case 4:
                                        // Get Score Operation.
                                        final GetScoreTask scoretsk = new GetScoreTask(server.database,
                                                server.onlineUsers, server.selector, key);
                                        server.threadpool.execute(scoretsk);
                                        break;
                                    case 5:
                                        // Get Scoreboard Operation.
                                        final GetScoreboardTask boardtsk = new GetScoreboardTask(server.database,
                                                server.onlineUsers, server.selector, key);
                                        server.threadpool.execute(boardtsk);
                                        break;
                                    case 6:
                                        // Match Operation.
                                        final String challenged = procArgs[1];
                                        final MatchTask matchtsk = new MatchTask(server.database, server.onlineUsers,
                                                server.matchAddressBook, server.selector, key, challenged,
                                                server.matchDuration, server.acceptDuration, server.numWords);
                                        server.threadpool.execute(matchtsk);
                                        break;
                                    default:
                                        break;
                                    }
                                }
                            }
                        } catch (final IOException IOE1) {
                            key.cancel();
                            try {
                                key.channel().close();
                            } catch (final IOException IOE2) {
                                IOE2.printStackTrace();
                            }
                        }
                    }
                }
            } catch (final IOException IOE) {
                IOE.printStackTrace();
            }
        }
    }

    /**
     * Utility method to display the client's hostname.
     * 
     * @param socketChannel the socket channel.
     * @return the client's hostname.
     */
    private String getClientHostname(final SocketChannel socketChannel) {
        return socketChannel.socket().getInetAddress().getHostName();
    }
}