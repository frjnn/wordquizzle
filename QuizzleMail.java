import java.nio.channels.SelectionKey;

/**
 * This is the class used to model the response message to be sent to a
 * QuizzleClient. Mails are sent by the {@code QuizzleServer's} Mailman thread.
 */
public class QuizzleMail {

    /**
     * The client's selection key.
     */
    private final SelectionKey key;

    /**
     * The message to be delivered.
     */
    private final String message;

    /**
     * Constructs a new QuizzleMail instance.
     * 
     * @param k the key.
     * @param s the message.
     */
    public QuizzleMail(SelectionKey k, String s) {
        this.key = k;
        this.message = s;
    }

    /**
     * Getter method for the {@code key} field.
     * 
     * @return the key.
     */
    public SelectionKey getKey() {
        return key;
    }

    /**
     * Getter method for the {@code message} field.
     * 
     * @return the message.
     */
    public String getMessage() {
        return message;
    }
}