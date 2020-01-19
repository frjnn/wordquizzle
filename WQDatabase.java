import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.*;
import java.io.*;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

/**
 * As the class name suggests, this is the {@code WQServer}'s database. The
 * WQDatabase class, in fact, take cares of storing and serializing all fo the
 * WQServer's persistent informations, such as the users nicknames and
 * passwords, the users friend lists and the users scores. All the serialization
 * is handled with the {@code Gson} library.
 */
public class WQDatabase {

    /* ---------------- Fields -------------- */

    /**
     * The concurrent hashmap of the class, it's the core of the database. The keys
     * are the users nicknames and the values are the corresponding WQUsers objects.
     */
    private ConcurrentHashMap<String, WQUser> userDB;

    /**
     * Returns a new WQDatabase.
     */
    public WQDatabase() {
        final File tmpDir = new File("Database.json");
        final boolean exists = tmpDir.exists();
        if (!exists) {
            this.userDB = new ConcurrentHashMap<String, WQUser>();
        } else {
            this.deserialize();
        }
    }

    /**
     * Sets the user's {@code nickname} score.
     * 
     * @param nickname the user's nickname.
     * @param score    the user's score.
     */
    public void setScore(String nickname, int score) {
        WQUser user = this.userDB.get(nickname);
        user.updateScore(score);
        try {
            // The database has been modified, must serialize.
            this.serialize();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Inserts the user in the concurrent hash map if absent and returns true else
     * it returns false if the nickname is already registered. Also serializes the
     * database.
     * 
     * @param nickname the user's nickname.
     * @param password the user's password.
     * @return {@code true} if the user has been added {@code false} otherwise.
     */
    public boolean insertUser(final String nickname, final String password) {
        final int hash = password.hashCode();
        final WQUser usr = new WQUser(nickname, hash);
        if (userDB.putIfAbsent(nickname, usr) == null) {
            try {
                this.serialize();
            } catch (final IOException ioe) {
                ioe.printStackTrace();
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Adds the user {@code friendNickname} to the user {@code nickname} friends
     * list.
     * 
     * @param nickname       the user that is adding the friend.
     * @param friendNickname the friend to be added.
     * @return {@code true} if the friend has been added {@code false} otherwise.
     */
    protected boolean addFriend(final String nickname, final String friendNickname) {
        WQUser user = this.retrieveUser(nickname);
        ArrayList<String> uFriends = user.getFriends();
        if (uFriends.contains(friendNickname))
            return false;
        else {
            uFriends.add(friendNickname);
            ArrayList<String> fFriends = this.retrieveUser(friendNickname).getFriends();
            fFriends.add(nickname);
            try {
                this.serialize();
            } catch (IOException IOE) {
                IOE.printStackTrace();
            }
            return true;
        }
    }

    /**
     * Retrieves the user {@code nickname}.
     * 
     * @param nickname the user nickname.
     * @return the {@code WQUser} named {@code nickname}.
     */
    protected WQUser retrieveUser(final String nickname) {
        final WQUser retUser = userDB.get(nickname);
        return retUser;
    }

    /**
     * The {@code WQDatabase} serialization method. Upon invocation writes on disk
     * {@code userDB}. The corresponding .json file is {@code Database.json}
     * 
     * @return a string containing userDB.
     * @throws IOException if something fishy happens while opening and writing the
     *                     file.
     */
    protected synchronized String serialize() throws IOException {
        final Gson gson = new Gson();
        final String json = gson.toJson(userDB);
        final FileWriter writer = new FileWriter("./Database.json");
        writer.write(json);
        writer.close();
        return json;
    }

    /**
     * The deserialization method, called on {@code WQServer} start.
     */
    protected void deserialize() {
        final Gson gson = new Gson();
        String json = null;
        final Type type = new TypeToken<ConcurrentHashMap<String, WQUser>>() {
        }.getType();
        try {
            json = new String(Files.readAllBytes(Paths.get("Database.json")), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            e.printStackTrace();
        }
        final ConcurrentHashMap<String, WQUser> deserializedMap = gson.fromJson(json, type);
        this.userDB = deserializedMap;
    }
}