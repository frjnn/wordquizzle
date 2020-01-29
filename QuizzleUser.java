import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The QuizzleUser class models a user and implements the comparable interface,
 * sorting the users is necessary to build the scoreboard. See the
 * {@link GetScoreboardTask} for more details.
 */
public class QuizzleUser implements Comparable<QuizzleUser> {

    /* ---------------- Fields -------------- */

    /**
     * The user's nickname.
     */
    private final String nickname;

    /**
     * The user's score.
     */
    private Integer score;

    /**
     * The hash of the user's password.
     */
    private final int pwdHash;

    /**
     * The user's friends.
     */
    private final ArrayList<String> friends;

    /**
     * Returns a new QuizzleUser.
     * 
     * @param nickname the nickname.
     * @param pwdHash  the password.
     */
    public QuizzleUser(final String nickname, final int pwdHash) {
        this.nickname = nickname;
        this.pwdHash = pwdHash;
        this.score = 0;
        this.friends = new ArrayList<String>();
    }

    /**
     * This is the default constructor used by Jackson to deserialize the
     * QuizzleServer's {@code database}.
     * 
     * @param nickname the nickname of the user.
     * @param pwdHash  the password hash of the user.
     * @param score    the score of the user.
     * @param friends  the friends of the user.
     */
    @JsonCreator
    public QuizzleUser(@JsonProperty("nickname") final String nickname, @JsonProperty("pwdHash") final int pwdHash,
            @JsonProperty("score") final int score, @JsonProperty("friends") final ArrayList<String> friends) {
        this.nickname = nickname;
        this.pwdHash = pwdHash;
        this.score = score;
        this.friends = friends;
    }

    /**
     * Getter method used to access the user's nickname.
     * 
     * @return the user's nickname.
     */
    protected String getNickname() {
        return this.nickname;
    }

    /**
     * Getter method used to access the user's score.
     * 
     * @return the user's score.
     */
    protected Integer getScore() {
        return this.score;
    }

    /**
     * Getter method used to access the user's nickname.
     * 
     * @return the user's password hash.
     */
    protected int getPwdHash() {
        return this.pwdHash;
    }

    /**
     * Getter method used to access the user's nickname.
     * 
     * @return the user's friends.
     */
    protected ArrayList<String> getFriends() {
        return this.friends;
    }

    /**
     * This method sets the user score. It is used upon match termination to update
     * the score of the players.
     * 
     * @param difference the increment or decrement to add to the score.
     */
    protected synchronized void updateScore(final Integer difference) {
        this.score += difference;
    }

    /**
     * Compare method. The scoreboard must be sorted by the user score and the
     * user's friend score. In order to do that QuizzleUser class implements the
     * comparable interface.
     * 
     * @param user another user to compare this with.
     */
    @Override
    public int compareTo(final QuizzleUser user) {
        return -1 * Integer.compare(this.score, user.score);
    }
}