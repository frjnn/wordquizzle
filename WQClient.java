import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;

/**
 * WQClient is the client program of WordQuizzle.
 */
public class WQClient {

    /* ---------------- Fields -------------- */

    /**
     * The InetSocketAddress of the WQServer.
     */
    private InetSocketAddress serverAddress;

    /**
     * The TCP socket used for server communication.
     */
    private Socket TCPSock;

    /**
     * The UDP socket used for accepting match invitations.
     */
    private DatagramSocket UDPSock;

    /**
     * The MatchListener class instance. The class implements the runnable
     * interface, it listens for match invitations.
     */
    private MatchListener UDPListener;

    /**
     * The thread on which MatchListener's run method is bein executed.
     */
    private Thread invitationsMonitor;

    /**
     * The user's nickname logged in on this WQClient instance.
     */
    private String myName;

    /**
     * The console used for keyboard input parsing.
     */
    private Console cons;

    /**
     * The ConcurrentHashMap where the pending challenges are stored.
     */
    private ConcurrentHashMap<String, DatagramPacket> challengers;

    /**
     * This is the constructor for the WQClient class. Returns a new WQClient.
     * 
     * @throws SocketException might be raised if the datagram socket could not be
     *                         opened, or the socket could not be bound to the
     *                         specified local port.
     */
    public WQClient() throws SocketException {
        TCPSock = new Socket();
        UDPSock = new DatagramSocket();
        challengers = new ConcurrentHashMap<String, DatagramPacket>();
        UDPListener = new MatchListener(UDPSock, challengers);
        invitationsMonitor = new Thread(UDPListener);
        invitationsMonitor.start();
        cons = System.console();
    }

    /**
     * Handles the RMI to register the user to the database.
     * 
     * @param nick the nickname of the user to be registered.
     * @param pwd  the password of the user.
     * @throws RemoteException might be raised if there's some problem with the
     *                         remote object
     */
    private void registration(final String nick, final String pwd) throws RemoteException {

        WQRegistrationRMI serverObj = null;
        Remote remoteObject = null;
        // Opening the registry and locating the remote object from it.
        final Registry reg = LocateRegistry.getRegistry(serverAddress.getHostName(), 5678);
        try {
            remoteObject = reg.lookup("REGISTRATION");
            serverObj = (WQRegistrationRMI) remoteObject;
        } catch (final Exception e) {
            e.printStackTrace();
        }
        // Calls the remote method and prints the result of its invocation.
        System.out.println(serverObj.registerUser(nick, pwd));
    }

    /**
     * Handles the login of an already registered user.
     * 
     * @param nick the nickname you want to login with
     * @param pwd  your password
     * @throws IOException might be raised if an error occurs during the socket
     *                     connection.
     */
    private void login(final String nick, final String pwd) throws IOException {
        // If the socket is not closed and it is not connected it means that the
        // WQClient class has just been created and the socket has been initialized but
        // is not connected.
        if (!TCPSock.isConnected() && !TCPSock.isClosed()) {
            TCPSock.connect(serverAddress);
        }
        // If the socket is closed it means that a logout has been executed by the
        // client thus the old socket TCP has been closed and a new one must be created.
        else if (TCPSock.isClosed()) {
            TCPSock = new Socket();
            TCPSock.connect(serverAddress);
        }
        // Sends a login request to the server and reads the result into response.
        final String response = clientComunicate(TCPSock, "0 " + nick + " " + pwd + " " + UDPSock.getLocalPort());
        System.out.println(response);
        if (response.equals("Login error: wrong password.")) {
            // Closes the socket because the login failed.
            TCPSock.close();
        }
        if (response.equals("Login successful.")) {
            // Assigns the name to this WQClient instance in order to recycle it for
            // subsequent requests.
            myName = nick;
        }
    }

    /**
     * Handles the logout of the user currently logged with this client.
     * 
     * @throws IOException if an I/O error occurs when closing the client socket.
     */
    private void logout() throws IOException {
        // Check if the user is logged in.
        if (!TCPSock.isConnected()) {
            System.out.println("You're not logged in.");
            return;
        }
        final String response = clientComunicate(TCPSock, "1");
        System.out.println(response);
        // closes socket if logout is successful
        if (response.equals("Logout successful."))
            TCPSock.close();
    }

    /**
     * Adds a friend to the user's friendlist.
     * 
     * @param friend the nickname of the user to add as a friend.
     */
    private void add_friend(final String friend) {
        // Check if the user is logged in.
        if (!TCPSock.isConnected()) {
            System.out.println("You're not logged in.");
            return;
        }
        // Sends a friend request and prints the server's response.
        System.out.println(clientComunicate(TCPSock, "2" + " " + friend));
    }

    /**
     * Shows the user's friendlist.
     */
    private void friend_list() {
        // Check if the user is connected.
        if (!TCPSock.isConnected()) {
            System.out.println("You're not logged in");
            return;
        }
        // Sends a friends list request and prints the user's friends.
        System.out.println(clientComunicate(TCPSock, "3"));
    }

    /**
     * Shows the user's score.
     */
    private void score() {
        // Check if the user is logged in.
        if (!TCPSock.isConnected()) {
            System.out.println("You're not logged in");
            return;
        }
        // Requests the score to the server and prints it.
        System.out.println(clientComunicate(TCPSock, "4"));
    }

    /**
     * Shows the user's scoreboard.
     */
    private void scoreboard() {
        // Check if the user is logged in.
        if (!TCPSock.isConnected()) {
            System.out.println("You're not logged in");
            return;
        }
        // Sends a scoreboard request to the server and prints the scoreboard for the
        // user himself and his friends.
        System.out.println(clientComunicate(TCPSock, "5"));
    }

    /**
     * Sends a match request to a friend.
     * 
     * @param friend the friend to challenge.
     * @throws IOException if the newly opened {@code challengeSock} incurrs in
     *                     problems during connection.
     */
    private void match(final String friend) throws IOException {
        // Check if the user is logged in;
        if (!TCPSock.isConnected()) {
            System.out.println("You're not logged in.");
            return;
        }
        // Sends a match request to a friend and blocks until the server communicates
        // acception or refusal, then stores the answer in resp.
        String resp = clientComunicate(TCPSock, "6" + " " + friend);
        // Splits the answer on the "/" char, if the match is accepted responseWords[1]
        // contains the port of the freshly opened server's side socket.
        String[] responseWords = resp.split("/");
        if (responseWords[0].equals(friend + " accepted your match invitation.")) {
            System.out.println(responseWords[0]);
            Socket challengeSock = new Socket();
            // Connects to the challenge socket.
            challengeSock
                    .connect(new InetSocketAddress(serverAddress.getAddress(), Integer.parseInt(responseWords[1])));
            // Starts the match logic.
            matchLogic(challengeSock);

        } else {
            // If the invitation has been refused it prints the response.
            System.out.println(responseWords[0]);
        }
    }

    /**
     * Shows all the pending match requests.
     */
    private void showMatches() {
        // Check if the user is logged in.
        if (!TCPSock.isConnected()) {
            System.out.println("You're not logged in.");
            return;
        }
        // If there are not any challenge requests.
        if (challengers.isEmpty()) {
            System.out.println("No pending challenges.");
            return;
        }
        // If there are, must print them.
        System.out.println("You have received match requests from the following friends:");
        for (String challenger : challengers.keySet()) {
            System.out.println(challenger);
        }

    }

    /**
     * Accepts a match sent by a friend.
     * 
     * @param friend the friend who sent you the request.
     * @throws IOException          if something fishy happens during the
     *                              {@code connect()}.
     * @throws InterruptedException if something fishy happens during the
     *                              {@code sleep()}.
     */
    private void acceptMatch(String friend) throws IOException, InterruptedException {
        // Check if the user is logged.
        if (!TCPSock.isConnected()) {
            System.out.println("You're not logged in.");
            return;
            // If there are no pending challenges.
        } else if (challengers.isEmpty()) {
            System.out.println("No pending challenges.");
            return;
            // If the user friend didn't send a challenge print an error message.
        } else if (!challengers.containsKey(friend)) {
            System.out.println("User " + friend + " didn't challenge you.");
            return;
        }
        // Gets the ip address and the port of the DatagramSocket of the server
        // using the information contained in the received UDP packet.
        InetAddress sockAddr = challengers.get(friend).getAddress();
        int port = challengers.get(friend).getPort();

        byte[] buf = "Y".getBytes();
        // Creates the acceptance datagram and sends it.
        DatagramPacket acceptance = new DatagramPacket(buf, buf.length, sockAddr, port);
        UDPSock.send(acceptance);
        DatagramPacket response = challengers.get(friend);
        String challenger = new String(response.getData(), response.getOffset(), response.getLength(),
                StandardCharsets.UTF_8);
        int TCPport = Integer.parseInt(challenger.substring(challenger.indexOf("/") + 1));
        // Since the match is been accepted the corresponding match invitation bust be
        // cancelled.
        challengers.remove(friend);
        // Sending the refusal packets to the other pending challengers invitations.
        for (DatagramPacket refusedFriend : challengers.values()) {
            buf = "N".getBytes();
            DatagramPacket refusal = new DatagramPacket(buf, buf.length, refusedFriend.getAddress(),
                    refusedFriend.getPort());
            UDPSock.send(refusal);
        }
        // Removes all the pending challengers from the hashmap.
        challengers.clear();
        // Sleeping in order to give the server enough time to set up the match socket.
        Thread.sleep(2000);
        // Creates and opens up a new socket where the matchLogic communication will
        // take place.
        Socket challengeSock = new Socket();
        challengeSock.connect(new InetSocketAddress(serverAddress.getAddress(), TCPport));
        matchLogic(challengeSock);
    }

    /**
     * Implements the logic of the match, waiting for words and sending user
     * translations back to the server.
     * 
     * @param sock the TCP socket opened for the match
     */
    private void matchLogic(Socket sock) {
        // Where the match logic is implemented.
        String input;
        // The first message sent to the server contains "START/" followed by the user's
        // nick. It is useful to differentiate the sockets server side.
        String resp = clientComunicate(sock, "START/" + myName);
        System.out.println("Translate all the following words:");
        System.out.println("Server: " + resp);
        String[] responseWords = resp.split(" ");
        while (!responseWords[0].equals("END")) {
            System.out.print("Translation: ");
            input = cons.readLine();
            input += "/" + myName;
            System.out.print("Server: ");
            resp = clientComunicate(sock, input);
            responseWords = resp.split("/");
            if (responseWords[0].equals("END")) {
                break;
            } else {
                System.out.println(resp);
            }
        }
        System.out.println(resp.substring(resp.indexOf("/") + 1));
        return;
    }

    /**
     * Parses the command line and calls the correct method in order to meet the
     * user's needs.
     * 
     * @param input the command line input read by the Console class.
     * @throws IOException          might be raised by login, logout or accept_match
     *                              methods.
     * @throws RemoteException      might be raised by the registration method.
     * @throws InterruptedException might be raised by the accept match method.
     */
    private void parseInput(final String input) throws IOException, RemoteException, InterruptedException {
        if (input == null) {
            System.out.println("Wrong Usage.");
            return;
        }
        final String[] params = input.split(" ");
        switch (params[0]) {
        case "register":
            registration(params[1], params[2]);
            break;
        case "login":
            login(params[1], params[2]);
            break;
        case "logout":
            logout();
            break;
        case "add_friend":
            add_friend(params[1]);
            break;
        case "friend_list":
            friend_list();
            break;
        case "score":
            score();
            break;
        case "scoreboard":
            scoreboard();
            break;
        case "match":
            match(params[1]);
            break;
        case "show_matches":
            showMatches();
            break;
        case "accept_match":
            acceptMatch(params[1]);
            break;
        default:
            System.out.println("Wrong Usage.");
            break;
        }
    }

    /**
     * Sends a message to the specified socket and returns a string with the
     * response to the caller.
     * 
     * @param sock the TCP socket where you wish to send the message.
     * @param arg  the message you want to send.
     * @return the returned string is the remote server's response.
     */
    private String clientComunicate(final Socket sock, final String arg) {
        // Gets the input and the output streams from the socket passed as argument.
        InputStream dataIn = null;
        OutputStream dataOut = null;
        try {
            dataIn = sock.getInputStream();
            dataOut = sock.getOutputStream();
        } catch (final Exception e) {
            e.printStackTrace();
        }
        // Creates a new InputStreamReader and decorates it with a BufferedReader.
        final InputStreamReader isr = new InputStreamReader(dataIn);
        final BufferedReader in = new BufferedReader(isr);

        byte[] request;
        request = arg.getBytes();
        try {
            // Sends the request on the socket.
            dataOut.write(request);
        } catch (final Exception e) {
            e.printStackTrace();
        }
        String response = null;
        try {
            // Blocks waiting from an answer from the server.
            response = in.readLine();
        } catch (final IOException e) {
            e.printStackTrace();
        }
        return response;
    }

    /**
     * It's a method that the client executes to automatically retrieve the running
     * WQServer instance's InetSocketAddress. The attempt to find the server lasts 5
     * seconds. It works by sending an UPD packet to the LAN broadcast address on
     * the port 9999. A WQServer, if there is one active, will eventually respond.
     * 
     * @return the WQServer InetSocketAddress that is being run on the LAN.
     * @throws SocketException if something fishy happens when opening the UDP
     *                         socket.
     * @throws IOException     if something fishy happens during the call to
     *                         {@code receive()}.
     */
    private InetSocketAddress probe() throws SocketException, IOException {
        DatagramSocket probeSocket = new DatagramSocket();
        // If a WQServer doesn't respond back after 5 seconds, consider the concetion
        // attempt failed.
        probeSocket.setSoTimeout(5000);
        // Sending and empty datagram.
        byte[] msg = ("").getBytes();
        DatagramPacket probe = new DatagramPacket(msg, msg.length,
                new InetSocketAddress(InetAddress.getByName("255.255.255.255"), 9999));
        probeSocket.send(probe);
        byte[] response = new byte[128];
        // Gets the response. The InetAddress is extracted from the UDP packet itself.
        DatagramPacket responsePacket = new DatagramPacket(response, response.length);
        try {
            probeSocket.receive(responsePacket);
        } catch (SocketTimeoutException e) {
            System.out.println("No WQServer found.");
            probeSocket.close();
            return null;
        }
        String contentString = new String(responsePacket.getData(), responsePacket.getOffset(),
                responsePacket.getLength(), StandardCharsets.UTF_8);
        InetSocketAddress serverAddress = new InetSocketAddress(responsePacket.getAddress(),
                Integer.valueOf(contentString));
        probeSocket.close();
        return serverAddress;
    }

    /**
     * Where the WQClient's logic takes place.
     * 
     * @param args the main args.
     * @throws RemoteException      if something fishy happens during the remote
     *                              method invocation.
     * @throws IOException          if something fishy happens during the WQClient
     *                              instance initialization.
     * @throws InterruptedException if something fishy happens while sleeping.
     */
    public static void main(final String[] args) throws RemoteException, IOException, InterruptedException {

        // Prints the correct usage.
        if (args.length > 0 && args[0].equals("--help")) {
            System.out.println("Commands:" + "\n" + "\tregister <username> <password> - registers the user." + "\n"
                    + "\tlogin - logs in an user." + "\n" + "\tlogout - logs out the user." + "\n"
                    + "\tadd_friend <nickFriend> - adds nickFriend as a friend." + "\n"
                    + "\tfriend_list - shows the friend lists." + "\n"
                    + "\tmatch <nickFriend> - sends a match request to a friend." + "\n"
                    + "\tshow_matches - shows the pending match invitations." + "\n"
                    + "\taccept_match <nickFriend> - accepts nickFriend's match invitation." + "\n"
                    + "\tscore - shows the user's score." + "\n" + "\tscoreboard - shows the user's scoreboard." + "\n"
                    + "\tquit - exits the WQWords client.");
            System.exit(0);
        }

        String input = null;
        // Creates a new WQClient instance.
        final WQClient client = new WQClient();

        try {
            // Probes the WQServer's InetSocketAddress from the LAN.
            client.serverAddress = client.probe();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // If no servers have been found exits whit status code 1.
        if (client.serverAddress == null)
            System.exit(1);

        // Sleeping.
        Thread.sleep(500);
        while (true) {

            // Reads the user input form the WQClient's console.
            input = client.cons.readLine("%s ", ">");

            // If the login operation has been requested must parse the nickname and the
            // password from the console.
            if (input.equals("login")) {
                String username;
                char[] passwd;
                if ((username = client.cons.readLine("%s ", "Username:")) != null
                        && (passwd = client.cons.readPassword("%s ", "Password:")) != null) {
                    input += " " + username + " " + String.valueOf(passwd);
                    client.cons.flush();
                }
            }
            // If the users passes the quit command, it quits the WQClient.
            if (input.equals("quit"))
                break;
            client.parseInput(input);
        }
        System.exit(0);
    }
}

/**
 * This class implements the UDP socket listener waiting to receive match
 * invitations.
 * 
 */
class MatchListener implements Runnable {

    DatagramSocket UDPSocket;
    ConcurrentHashMap<String, DatagramPacket> challengers;

    /**
     * The constructor to MatchListener.
     * 
     * @param UDPSock     the UDP socket where the listener will wait for
     *                    invitations
     * @param challengers the HashMap all pending invitations are put
     */
    MatchListener(DatagramSocket sock, ConcurrentHashMap<String, DatagramPacket> map) throws SocketException {
        UDPSocket = sock;
        challengers = map;
    }

    public void run() {
        while (true) {
            byte[] buf = new byte[512];
            DatagramPacket response = new DatagramPacket(buf, buf.length);
            try {
                // Receives match invitations.
                UDPSocket.receive(response);
                System.out.println("You have a notification:");
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Extract the content from the packet.
            String contentString = new String(response.getData(), response.getOffset(), response.getLength(),
                    StandardCharsets.UTF_8);
            // If the invitation timed out it removes the corresponding match invitation
            // from challenger.
            if (contentString.substring(0, contentString.indexOf("/")).equals("TIMEOUT")) {
                String timedOutChallenger = contentString.substring(contentString.indexOf("/") + 1);
                challengers.remove(timedOutChallenger);
                System.out.println(timedOutChallenger + "'s match request timed out.");
                continue;
            }
            String challenger = contentString.substring(0, contentString.indexOf("/"));
            System.out.println("Received a challenge from: " + challenger);
            System.out.print("> ");
            // Puts the challenger in the pending list.
            challengers.put(challenger, response);
        }
    }
}