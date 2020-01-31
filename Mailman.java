import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The Mailman class is a runnable class ran by the QuizzleServer's thread whose
 * job is to deliver the result of the operations to the client who requested
 * them. It has two fields: the {@code postDepot} field where the mail to
 * deliver is stored and the {@code selector} field where a reference to the
 * QuizzleServer's selector is kept, in order to perform the wakeup operation
 * and to also remove the key after a succesful logout operation. The task
 * remains in executin as long as the server is up.
 */
public class Mailman implements TaskInterface {

    /**
     * The QuizzleServer's post depot.
     */
    private final LinkedBlockingQueue<QuizzleMail> postDepot;

    /**
     * The Mailman constructor.
     * 
     * @param sel   the selector.
     * @param queue the post depot.
     */
    public Mailman(Selector sel, LinkedBlockingQueue<QuizzleMail> queue) {
        this.postDepot = queue;
    }

    /**
     * Run method.
     */
    public void run() {
        while (true) {
            // Retrieving the first mail in the depot.
            QuizzleMail mail = null;
            try {
                // Popping the list head.
                mail = postDepot.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // Getting the key. The key can be seen as the addres to deliver the
            // message to.
            SelectionKey key = mail.getKey();
            // Getting the message.
            String message = mail.getMessage();
            final SocketChannel clientSocket = (SocketChannel) key.channel();
            final ByteBuffer bBuff = (ByteBuffer) key.attachment();
            // Writing the message.
            TaskInterface.writeMsg(message, bBuff, clientSocket);
            // If the client logged out, the mailman "throws away his mailbox".
            if (!message.equals("Logout successful.\n")) {
                key.interestOps(SelectionKey.OP_READ);
                // selector.wakeup();
            } else {
                try {
                    clientSocket.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        }
    }
}