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
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.util.concurrent.ConcurrentHashMap;

import java.nio.charset.StandardCharsets;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * WQClientGUI is the client program of WordQuizzle provided with a GUI.
 */
public class WQClientGUI {

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
    private final DatagramSocket UDPSock;

    /**
     * The MatchListener class instance. The class implements the runnable
     * interface, it listens for match invitations.
     */
    private final MatchListener UDPListener;

    /**
     * The thread on which MatchListener's run method is bein executed.
     */
    private final Thread invitationsMonitor;

    /**
     * The user's nickname logged in on this WQClientGUI instance.
     */
    private String myName;

    /**
     * The ConcurrentHashMap where the pending challenges are stored.
     */
    private final ConcurrentHashMap<String, DatagramPacket> challengers;

    /**
     * The client's graphic user interface.
     */
    private JFrame gui;

    /**
     * This is the constructor for the WQClientGUI class. Returns a new WQClientGUI.
     * 
     * @throws SocketException might be raised if the datagram socket could not be
     *                         opened, or the socket could not be bound to the
     *                         specified local port.
     */
    public WQClientGUI() throws SocketException {
        TCPSock = new Socket();
        UDPSock = new DatagramSocket();
        challengers = new ConcurrentHashMap<String, DatagramPacket>();
        UDPListener = new MatchListener(UDPSock, challengers, gui);
        invitationsMonitor = new Thread(UDPListener);
        invitationsMonitor.start();
        gui = this.setMainFrame();
    }

    /**
     * Handles the RMI to register the user to the database.
     * 
     * @param nick the nickname of the user to be registered.
     * @param pwd  the password of the user.
     * @throws RemoteException might be raised if there's some problem with the
     *                         remote object
     */
    private String registration(final String nick, final String pwd) throws RemoteException {

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
        return serverObj.registerUser(nick, pwd);
    }

    /**
     * Handles the login of an already registered user.
     * 
     * @param nick the nickname you want to login with
     * @param pwd  your password
     * @throws IOException might be raised if an error occurs during the socket
     *                     connection.
     */
    private String login(final String nick, final String pwd) throws IOException {
        // If the socket is not closed and it is not connected it means that the
        // WQClientGUI class has just been created and the socket has been initialized
        // but
        // is not connected.
        if (!TCPSock.isConnected() && !TCPSock.isClosed()) {
            TCPSock.connect(serverAddress);
        }
        // If the socket is closed it means that a logoutButton has been executed by the
        // client thus the old socket TCP has been closed and a new one must be created.
        else if (TCPSock.isClosed()) {
            TCPSock = new Socket();
            TCPSock.connect(serverAddress);
        }
        // Sends a login request to the server and reads the result into response.
        final String response = clientComunicate(TCPSock, "0 " + nick + " " + pwd + " " + UDPSock.getLocalPort());
        if (response.equals("Login error: wrong password.")) {
            // Closes the socket because the login failed.
            TCPSock.close();
        }
        if (response.equals("Login successful.")) {
            // Assigns the name to this WQClientGUI instance in order to recycle it for
            // subsequent requests.
            myName = nick;
        }
        return response;
    }

    /**
     * Handles the logout of the user currently logged with this client.
     * 
     * @throws IOException if a I/O error occurs when closing the client socket.
     */
    private void logout() throws IOException {
        final String response = clientComunicate(TCPSock, "1");
        // Closes socket if logout is successful
        if (response.equals("Logout successful."))
            TCPSock.close();
    }

    /**
     * Adds a friend to the user's friendlist.
     * 
     * @param friend the nickname of the user to add as a friend.
     */
    private String add_friend(final String friend) {
        // Sends a friend request and returns the server's response.
        return clientComunicate(TCPSock, "2" + " " + friend);
    }

    /**
     * Shows the user's friendlist.
     */
    private String friend_list() {
        // Sends a friends list request and returns the user's friends.
        return clientComunicate(TCPSock, "3");
    }

    /**
     * Shows the user's score.
     */
    private String score() {
        // Requests the score to the server and returns it.
        return clientComunicate(TCPSock, "4");
    }

    /**
     * Shows the user's scoreboard.
     */
    private String scoreboard() {
        // Sends a scoreboard request to the server and returns the scoreboard.
        return clientComunicate(TCPSock, "5");
    }

    /**
     * Sends a match request to a friend.
     * 
     * @param friend the friend to challenge.
     * @throws IOException if the newly opened {@code challengeSock} incurrs in
     *                     problems during connection.
     */
    private String match(final String friend) throws IOException {
        // Sends a match request to a friend and blocks until the server communicates
        // acception or refusal, then stores the answer in resp.
        final String resp = clientComunicate(TCPSock, "6" + " " + friend);
        // Splits the answer on the "/" char, if the match is accepted responseWords[1]
        // contains the port of the freshly opened server's side socket.
        final String[] responseWords = resp.split("/");
        if (responseWords[0].equals(friend + " accepted your match invitation.")) {

            final Socket challengeSock = new Socket();
            // Connects to the challenge socket.
            challengeSock
                    .connect(new InetSocketAddress(serverAddress.getAddress(), Integer.parseInt(responseWords[1])));
            // Starts the match logic.
            JOptionPane.showMessageDialog(gui, responseWords[0], "WordQuizzle - Info", JOptionPane.INFORMATION_MESSAGE);
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            setChallengeFrame(challengeSock);
            return responseWords[0];
        } else {
            // If the invitation has been refused it prints the response.
            return responseWords[0];
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
    private void acceptMatch(final String friend) throws IOException, InterruptedException {
        // Gets the ip address and the port of the DatagramSocket of the server
        // using the information contained in the received UDP packet.
        final InetAddress sockAddr = challengers.get(friend).getAddress();
        final int port = challengers.get(friend).getPort();

        byte[] buf = "Y".getBytes();
        // Creates the acceptance datagram and sends it.
        final DatagramPacket acceptance = new DatagramPacket(buf, buf.length, sockAddr, port);
        UDPSock.send(acceptance);
        final DatagramPacket response = challengers.get(friend);
        final String challenger = new String(response.getData(), response.getOffset(), response.getLength(),
                StandardCharsets.UTF_8);
        final int TCPport = Integer.parseInt(challenger.substring(challenger.indexOf("/") + 1));
        // Since the match is been accepted the corresponding match invitation bust be
        // cancelled.
        challengers.remove(friend);
        // Sending the refusal packets to the other pending challengers invitations.
        for (final DatagramPacket refusedFriend : challengers.values()) {
            buf = "N".getBytes();
            final DatagramPacket refusal = new DatagramPacket(buf, buf.length, refusedFriend.getAddress(),
                    refusedFriend.getPort());
            UDPSock.send(refusal);
        }
        // Removes all the pending challengers from the hashmap.
        challengers.clear();
        // Sleeping in order to give the server enough time to set up the match socket.
        Thread.sleep(2000);
        // Creates and opens up a new socket where the matchLogic communication will
        // take place.
        final Socket challengeSock = new Socket();
        challengeSock.connect(new InetSocketAddress(serverAddress.getAddress(), TCPport));
        setChallengeFrame(challengeSock);
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
        final DatagramSocket probeSocket = new DatagramSocket();
        // If a WQServer doesn't respond back after 5 seconds, consider the concetion
        // attempt failed.
        probeSocket.setSoTimeout(5000);
        // Sending and empty datagram.
        final byte[] msg = ("").getBytes();
        final DatagramPacket probe = new DatagramPacket(msg, msg.length,
                new InetSocketAddress(InetAddress.getByName("255.255.255.255"), 9999));
        probeSocket.send(probe);
        final byte[] response = new byte[128];
        // Gets the response. The InetAddress is extracted from the UDP packet itself.
        final DatagramPacket responsePacket = new DatagramPacket(response, response.length);
        try {
            probeSocket.receive(responsePacket);
        } catch (final SocketTimeoutException e) {
            System.out.println("No WQServer found.");
            probeSocket.close();
            return null;
        }
        final String contentString = new String(responsePacket.getData(), responsePacket.getOffset(),
                responsePacket.getLength(), StandardCharsets.UTF_8);
        final InetSocketAddress serverAddress = new InetSocketAddress(responsePacket.getAddress(),
                Integer.valueOf(contentString));
        probeSocket.close();
        return serverAddress;
    }

    /* ---------------- GUI -------------- */

    /**
     * Sets up the main frame of the client's graphic user interface.
     * 
     * @return the newly created main frame.
     */
    private JFrame setMainFrame() {
        // Creating frame.
        final JFrame mainFrame = new JFrame("Word Quizzle");
        mainFrame.setSize(500, 300);
        mainFrame.setVisible(false);
        mainFrame.setResizable(false);
        mainFrame.setLayout(null);
        // Setting frame components.
        final JLabel matchHubLabel = new JLabel("Match Hub");
        final JLabel scoreBoardLabel = new JLabel("Score Board");
        final JLabel socialHubLabel = new JLabel("Social Hub");
        final JLabel addFriendLabel = new JLabel("Add friend:");
        final JTextField username = new JTextField();
        final JButton scoreButton = new JButton("Score");
        final JButton scoreBoardButton = new JButton("Scoreboard");
        final JButton logoutButton = new JButton("Logout");
        final JButton addFriendButton = new JButton("Add");
        final JButton friendListButton = new JButton("Friend List");
        final JButton matchButton = new JButton("Match");
        scoreButton.setFont(new Font("Arial", Font.PLAIN, 10));
        scoreBoardButton.setFont(new Font("Arial", Font.PLAIN, 10));
        logoutButton.setFont(new Font("Arial", Font.PLAIN, 10));
        addFriendButton.setFont(new Font("Arial", Font.PLAIN, 10));
        friendListButton.setFont(new Font("Arial", Font.PLAIN, 10));
        matchButton.setFont(new Font("Arial", Font.PLAIN, 10));
        // Placing components.
        matchHubLabel.setBounds(85, 0, 100, 20);
        scoreBoardLabel.setBounds(25, 70, 100, 20);
        socialHubLabel.setBounds(335, 0, 100, 20);
        addFriendLabel.setBounds(290, 30, 100, 20);
        username.setBounds(290, 60, 170, 20);
        scoreButton.setBounds(25, 20, 200, 30);
        scoreBoardButton.setBounds(25, 60, 200, 30);
        logoutButton.setBounds(200, 220, 100, 20);
        addFriendButton.setBounds(275, 90, 90, 20);
        friendListButton.setBounds(385, 90, 90, 20);
        // Adding componenets to the frame.
        mainFrame.add(username);
        mainFrame.add(addFriendLabel);
        mainFrame.add(matchHubLabel);
        mainFrame.add(socialHubLabel);
        mainFrame.add(scoreButton);
        mainFrame.add(scoreBoardButton);
        mainFrame.add(logoutButton);
        mainFrame.add(addFriendButton);
        mainFrame.add(friendListButton);
        mainFrame.add(matchButton);
        // Initializing the friend list and the scoreboard list.
        final DefaultListModel<String> friendListModel = new DefaultListModel<String>();
        final JList<String> friendList = new JList<String>(friendListModel);
        final DefaultListModel<String> scoreBoardListModel = new DefaultListModel<String>();
        final JList<String> scoreBoard = new JList<String>(scoreBoardListModel);
        scoreBoard.setVisible(false);
        friendList.setVisible(false);
        mainFrame.add(friendList);
        mainFrame.add(scoreBoard);

        // Enables the top right button "X".
        mainFrame.addWindowListener(new WindowAdapter() {
            public void windowClosing(final WindowEvent e) {
                e.getWindow().dispose();
            }

            public void windowClosed(final WindowEvent e) {
                System.exit(0);
            }
        });

        // Logout button action listener.
        logoutButton.addActionListener(e -> {
            try {
                logout();
            } catch (final Exception ex) {
                ex.printStackTrace();
            }
            mainFrame.dispose();
            System.exit(0);
        });

        // Add friend button action listener.
        addFriendButton.addActionListener(e -> {
            if (username.getText().isEmpty()) {
                JOptionPane.showMessageDialog(mainFrame, "Please insert a valid nickname.", "WordQuizzle - Error",
                        JOptionPane.INFORMATION_MESSAGE);
            } else {
                String response = null;
                try {
                    response = add_friend(username.getText());
                } catch (final Exception ex) {
                    ex.printStackTrace();
                }
                if (response.equals(username.getText() + " " + "is now your friend."))
                    JOptionPane.showMessageDialog(mainFrame, response, "Word Quizzle - Info",
                            JOptionPane.INFORMATION_MESSAGE);
                else
                    JOptionPane.showMessageDialog(mainFrame, response, "Word Quizzle - Error",
                            JOptionPane.INFORMATION_MESSAGE);
            }
            username.setText("");
        });

        // Friend list button action listener.
        friendListButton.addActionListener(e -> {
            if (!friendList.isVisible()) {
                friendList.setLocation(275, 30);
                friendList.setSize(200, 110);
                mainFrame.remove(addFriendLabel);
                mainFrame.remove(username);
                mainFrame.remove(addFriendButton);
                final String response = friend_list();
                if (!response.equals("You currently have no friends, add some!")) {
                    String refine = response.replace("Your friends are:", "");
                    final String[] friends = refine.split(" ");
                    for (final String s : friends) {
                        if (!s.equals(""))
                            friendListModel.addElement(s);
                    }
                }
                socialHubLabel.setText("Friend List");
                friendList.setVisible(true);
                friendList.setEnabled(false);
                friendListButton.setText("Close");
                friendListButton.setBounds(325, 150, 100, 20);
            } else {
                friendList.setVisible(false);
                friendListModel.clear();
                friendList.setVisible(false);
                mainFrame.add(addFriendLabel);
                mainFrame.add(username);
                mainFrame.add(addFriendButton);
                friendListButton.setVisible(false);
                friendListButton.setLocation(385, 90);
                friendListButton.setVisible(true);
                friendListButton.setText("Friend List");
                socialHubLabel.setText("Social Hub");
            }
        });

        // Score button action listener.
        scoreButton.addActionListener(e -> {
            final String response = score();
            JOptionPane.showMessageDialog(mainFrame, response, "Word Quizzle - Info", JOptionPane.INFORMATION_MESSAGE);
        });

        // Score board button action listener.
        scoreBoardButton.addActionListener(e -> {
            if (!scoreBoard.isVisible()) {
                scoreBoard.setLocation(25, 60);
                scoreBoard.setSize(200, 110);
                scoreBoardButton.setBounds(20, 180, 100, 20);
                matchButton.setBounds(130, 180, 100, 20);
                matchButton.setVisible(true);
                final String response = scoreboard();
                final String[] friends = response.split(" ");
                int i = 1;
                for (int j = 0; j < friends.length - 1; j += 2) {
                    if (!friends[j].equals(""))
                        scoreBoardListModel.addElement(i + ". " + friends[j] + " " + friends[j + 1]);
                    i++;
                }
                scoreBoard.setVisible(true);
                scoreBoardButton.setText("Close");
            } else {
                scoreBoard.setVisible(false);
                scoreBoardListModel.clear();
                scoreBoard.setVisible(false);
                scoreBoardButton.setBounds(25, 60, 200, 30);
                scoreBoardButton.setText("Scoreboard");
                matchButton.setVisible(false);
            }
        });

        // Match button action listener.
        matchButton.addActionListener(e -> {
            int index = scoreBoard.getSelectedIndex();
            if (index != -1) {
                final String selected = scoreBoard.getSelectedValue();
                final String[] args = selected.split(" ");
                final String nick = args[1];
                String response = null;
                if (selected != null) {
                    scoreBoard.setVisible(false);
                    scoreBoardListModel.clear();
                    scoreBoard.setVisible(false);
                    scoreBoardButton.setBounds(25, 60, 200, 30);
                    scoreBoardButton.setText("Scoreboard");
                    matchButton.setVisible(false);
                    if (challengers.containsKey(nick)) {
                        try {
                            acceptMatch(nick);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    } else {
                        try {
                            response = match(nick);
                            if (!response.equals(nick + " accepted your match invitation."))
                                JOptionPane.showMessageDialog(gui, response, "WordQuizzle - Info",
                                        JOptionPane.INFORMATION_MESSAGE);
                        } catch (final IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            }
        });
        return mainFrame;
    }

    /**
     * Sets up the login frame.
     */
    private void setLoginFrame() {
        // Creates the frame.
        final JFrame login = new JFrame("Word Quizzle - Login");
        // Sets the frame size.
        login.setSize(420, 200);
        login.setLocation(400, 175);
        login.setResizable(false);
        login.setLayout(null);
        // Creation of the frame components.
        final JLabel usr = new JLabel("Username:");
        final JLabel psw = new JLabel("Password:");
        final JButton ok = new JButton("OK");
        final JButton register = new JButton("Register");
        final JButton exit = new JButton("Exit");
        // Setting buttons font.
        ok.setFont(new Font("Arial", Font.PLAIN, 10));
        register.setFont(new Font("Arial", Font.PLAIN, 10));
        exit.setFont(new Font("Arial", Font.PLAIN, 10));
        final JTextField username = new JTextField();
        final JPasswordField password = new JPasswordField("");
        password.setEchoChar('*');
        final JCheckBox hidePw = new JCheckBox("Show password", Boolean.FALSE);
        // Setting components position.
        usr.setBounds(35, 30, 130, 20);
        psw.setBounds(35, 70, 130, 20);
        ok.setBounds(35, 130, 75, 20);
        register.setBounds(172, 130, 75, 20);
        exit.setBounds(310, 130, 75, 20);
        username.setBounds(172, 30, 170, 20);
        password.setBounds(172, 70, 170, 20);
        hidePw.setBounds(172, 90, 200, 30);
        // Adding components to frame.
        login.add(usr);
        login.add(psw);
        login.add(ok);
        login.add(register);
        login.add(exit);
        login.add(username);
        login.add(password);
        login.add(hidePw);

        // Enables the top right button "X".
        login.addWindowListener(new WindowAdapter() {
            public void windowClosing(final WindowEvent e) {
                e.getWindow().dispose();
            }

            public void windowClosed(final WindowEvent e) {
                System.exit(0);
            }
        });

        // Listener for the password check box. It hides the password.
        hidePw.addItemListener(e -> {
            if (e.getStateChange() != ItemEvent.SELECTED) {
                password.setEchoChar('*');
            } else {
                password.setEchoChar((char) 0);
            }
        });

        // Listener for the ok button.
        ok.addActionListener(e -> {
            if ((!username.getText().isEmpty() && !(password.getPassword().length == 0))) {
                String response = null;
                try {
                    response = login(username.getText(), new String(password.getPassword()));
                } catch (final Exception ex) {
                    ex.printStackTrace();
                }
                if (response.equals("Login successful.")) {
                    gui.setLocation(login.getLocation());
                    gui.setTitle("Word Quizzle" + " - " + username.getText());
                    gui.setVisible(true);
                    login.setVisible(false);
                } else {
                    JOptionPane.showMessageDialog(login, response, "WordQuizzle - Login Error",
                            JOptionPane.INFORMATION_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(login, "Please valid username and password.", "WordQuizzle - Login Error",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });

        // Listener for the register button. Upon being clicked the registration frame
        // is fcreated and shown.
        register.addActionListener(e -> {
            login.setVisible(Boolean.FALSE);
            setRegistrationFrame(login);
        });

        // Exit button listener. As the same effect of clicking the top rght corner "X".
        exit.addActionListener(e -> login.dispose());
        // Last thing to do, sets the login frame visibility.
        login.setVisible(true);
    }

    /**
     * Creates the registration frame.
     * 
     * @param login The login frame.
     */
    private void setRegistrationFrame(final JFrame login) {
        // Frame creation.
        final JFrame registrationFrame = new JFrame("Word Quizzle - Registration");
        // Setting the frame size and location. Here is used the login frame location.
        registrationFrame.setSize(420, 200);
        registrationFrame.setLocation(login.getLocation());
        registrationFrame.setVisible(true);
        registrationFrame.setResizable(Boolean.FALSE);
        registrationFrame.setLayout(null);
        // Creation of the frame components.
        final JLabel username = new JLabel("Username");
        final JLabel psw = new JLabel("Password");
        final JButton register = new JButton("Register");
        final JButton cancel = new JButton("Cancel");
        register.setFont(new Font("Arial", Font.PLAIN, 10));
        cancel.setFont(new Font("Arial", Font.PLAIN, 10));
        final JTextField nickname = new JTextField();
        final JPasswordField password = new JPasswordField("");
        password.setEchoChar('*');
        final JCheckBox hidePw = new JCheckBox("Show password", Boolean.FALSE);
        // Setting componenets position.
        username.setBounds(35, 30, 130, 20);
        psw.setBounds(35, 70, 130, 20);
        register.setBounds(35, 130, 95, 20);
        cancel.setBounds(290, 130, 95, 20);
        nickname.setBounds(172, 30, 170, 20);
        password.setBounds(172, 70, 170, 20);
        hidePw.setBounds(172, 90, 200, 30);
        // Adding the components to the frame.
        registrationFrame.add(username);
        registrationFrame.add(psw);
        registrationFrame.add(register);
        registrationFrame.add(cancel);
        registrationFrame.add(nickname);
        registrationFrame.add(password);
        registrationFrame.add(hidePw);

        // Enables the top right button "X".
        registrationFrame.addWindowListener(new WindowAdapter() {
            public void windowClosing(final WindowEvent e) {
                e.getWindow().dispose();
            }

            // If closed shows the login window again.
            public void windowClosed(final WindowEvent e) {
                login.setVisible(Boolean.TRUE);
            }
        });

        // Listener for the password checkbox.
        hidePw.addItemListener(e -> {
            if (e.getStateChange() != ItemEvent.SELECTED) {
                password.setEchoChar('*');
            } else {
                password.setEchoChar((char) 0);
            }
        });

        // Register button listener.
        register.addActionListener(e -> {
            if (!nickname.getText().isEmpty() && !(password.getPassword().length == 0)) {
                String esito = null;
                try {
                    esito = registration(nickname.getText(), new String(password.getPassword()));
                } catch (final Exception ex) {
                    ex.printStackTrace();
                }
                if (esito.equals("Registration succeeded.")) {
                    // Registrazione riuscita
                    JOptionPane.showMessageDialog(registrationFrame, esito, "Word Quizzle - Success",
                            JOptionPane.INFORMATION_MESSAGE);
                    registrationFrame.dispose();
                } else {
                    // Se username gia presente
                    JOptionPane.showMessageDialog(registrationFrame, "Registrazione Fallita:\nUsername già presente",
                            "Word Quizzle - Error", JOptionPane.INFORMATION_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(registrationFrame, "Please enter a valid username and password.",
                        "Word Quizzle - Error", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        // Cancel button listener.
        cancel.addActionListener(e -> {
            // If the user clicks cancel it closes the window.
            registrationFrame.dispose();
        });
    }

    /**
     * Sets the challange frame.
     * 
     * @param s the match socket.
     */
    private void setChallengeFrame(Socket s) {
        // Disables the main frame when the match starts.
        gui.setEnabled(false);
        // Frame creation.
        final JFrame challengeFrame = new JFrame("Match");
        // Setting frame size.
        challengeFrame.setSize(200, 200);
        challengeFrame.setLocation(gui.getLocation());
        challengeFrame.setResizable(false);
        challengeFrame.setLayout(null);
        challengeFrame.setVisible(true);
        challengeFrame.setResizable(Boolean.FALSE);
        challengeFrame.setLayout(null);
        // Creation of the frame components.
        JLabel word = new JLabel();
        JButton submitButton = new JButton("Submit");
        submitButton.setFont(new Font("Arial", Font.PLAIN, 10));
        TextField insert = new TextField();
        // Adding the components to the frame.
        word.setBounds(35, 30, 130, 20);
        insert.setBounds(35, 70, 130, 20);
        submitButton.setBounds(35, 130, 95, 20);
        challengeFrame.add(word);
        challengeFrame.add(insert);
        challengeFrame.add(submitButton);

        // This boolean indicates if the mymemoryAPI is available or not.
        boolean service = true;
        // The first message sent to the server contains "START/" followed by the user's
        // nick. It is useful to differentiate the sockets server side.
        String resp = clientComunicate(s, "START/" + myName);
        // If the servers comunicates that the translation api is unavailable the match
        // doesn't take place.
        if (resp.equals("Sorry, the translation service is unavailable. Try later.")) {
            service = false;
            JOptionPane.showMessageDialog(challengeFrame, resp, "Word Quizzle - Info", JOptionPane.INFORMATION_MESSAGE);
        }
        if (service) {
            // If the API service is available.
            JOptionPane.showMessageDialog(challengeFrame, "Translate all the following words!", "Word Quizzle - Info",
                    JOptionPane.INFORMATION_MESSAGE);
            word.setText(resp);
            submitButton.addActionListener(e -> {
                String input = insert.getText() + "/" + myName;
                String res = clientComunicate(s, input);
                String[] a = res.split("/");
                if (a[0].equals("END")) {
                    submitButton.setEnabled(false);
                    word.setText("");
                    insert.setEnabled(false);
                    gui.setEnabled(true);
                    JOptionPane.showMessageDialog(gui, res.substring(res.indexOf("/") + 1), "Word Quizzle - Results",
                            JOptionPane.INFORMATION_MESSAGE);
                    challengeFrame.dispose();
                } else {
                    word.setText(res);
                    insert.setText("");
                }
            });
        } else {
            challengeFrame.dispose();
            gui.setEnabled(true);
        }
    }

    /**
     * Where the WQClientGUI's logic takes place.
     * 
     * @param args the main args.
     * @throws RemoteException      if something fishy happens during the remote
     *                              method invocation.
     * @throws IOException          if something fishy happens during the
     *                              WQClientGUI instance initialization.
     * @throws InterruptedException if something fishy happens while sleeping.
     */
    public static void main(final String[] args) throws RemoteException, IOException, InterruptedException {

        // Creates a new WQClientGUI instance.
        final WQClientGUI client = new WQClientGUI();

        try {
            // Probes the WQServer's InetSocketAddress from the LAN.
            client.serverAddress = client.probe();
        } catch (final IOException e) {
            e.printStackTrace();
        }

        // If no servers have been found exits with status code 1.
        if (client.serverAddress == null)
            System.exit(1);

        // Sets the initial gui's frame.
        client.setLoginFrame();
    }
}

/**
 * This class implements the UDP socket listener waiting to receive match
 * invitations.
 */
class MatchListener implements Runnable {

    DatagramSocket UDPSocket;
    ConcurrentHashMap<String, DatagramPacket> challengers;
    JFrame mainFrame;

    /**
     * The constructor to MatchListener.
     * 
     * @param UDPSock     the UDP socket where the listener will wait for
     *                    invitations
     * @param challengers the HashMap all pending invitations are put
     * @param main        the mainFrame of the gui.
     */
    MatchListener(final DatagramSocket sock, final ConcurrentHashMap<String, DatagramPacket> map, final JFrame main)
            throws SocketException {
        UDPSocket = sock;
        challengers = map;
        mainFrame = main;
    }

    public void run() {
        while (true) {
            final byte[] buf = new byte[512];
            final DatagramPacket response = new DatagramPacket(buf, buf.length);
            try {
                // Receives match invitations.
                UDPSocket.receive(response);
            } catch (final IOException e) {
                e.printStackTrace();
            }
            // Extract the content from the packet.
            final String contentString = new String(response.getData(), response.getOffset(), response.getLength(),
                    StandardCharsets.UTF_8);
            // If the invitation timed out it removes the corresponding match invitation
            // from challenger.
            if (contentString.substring(0, contentString.indexOf("/")).equals("TIMEOUT")) {
                final String timedOutChallenger = contentString.substring(contentString.indexOf("/") + 1);
                challengers.remove(timedOutChallenger);
                continue;
            }
            final String challenger = contentString.substring(0, contentString.indexOf("/"));
            // Creates a notification to alert the user.
            JOptionPane.showMessageDialog(mainFrame, "Received a challenge from: " + challenger,
                    "Word Quizzle - Notification", JOptionPane.INFORMATION_MESSAGE);
            // Puts the challenger in the pending list.
            challengers.put(challenger, response);
        }
    }
}