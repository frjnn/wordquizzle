import java.util.HashMap;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.URL;
import java.io.FileReader;
import java.util.Random;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The MatchWords class implements the random retrieval of the specified number
 * of words to be fed to two players during a match. It also takes care of
 * translating the randomly choosen words by sending GET requests to the
 * MyMemory API.
 */
public class MatchWords {

    /* ---------------- Fields -------------- */

    /**
     * The number of words.
     */
    private int wordNum;

    /**
     * The dictionary, contains wordNum words.
     */
    public ArrayList<String> dictionary;

    /**
     * Returns a new MatchWords object and populates the {@code dictionary}
     * ArrayList used to extract the match words.
     * 
     * @param wordNum the number of words to request.
     */
    public MatchWords(int wordNum) {
        this.wordNum = wordNum;
        this.dictionary = new ArrayList<String>();
        try (BufferedReader reader = new BufferedReader(new FileReader("ItalianDictionary.txt"));) {
            String word;
            while ((word = reader.readLine()) != null) {
                this.dictionary.add(word);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Translates a single word contacting the remote MyMemory API. Returns an
     * ArrayList containing the traslations of the words, since there may be more
     * than one translation.
     * 
     * @param word the word to translate.
     * @throws IOException if some problems occur during the translation process.
     *                     This exception is very important because it signals to
     *                     the method's caller that the service is currently
     *                     unavailable.
     */
    private ArrayList<String> getTranslation(String word) throws IOException {
        // The ArrayList where all the possible translations of the string word will be
        // stored.
        ArrayList<String> translations = new ArrayList<String>();
        // Setting the GET request. The server disctionary contains composite words so
        // whitespaces must be replaced by the corresponding percent encoding.
        String HTTPrequest = "https://api.mymemory.translated.net/get?q=" + word.replace(" ", "%20")
                + "&langpair=it|en";
        JsonFactory jfactory = new JsonFactory();
        // Creating a Jackson's JasonParser to parse the GET result.
        JsonParser jParser = jfactory.createParser(new URL(HTTPrequest));
        // Mapping the result into a JsonNode.
        ObjectMapper mapper = new ObjectMapper();
        JsonNode input = mapper.readTree(jParser);
        final JsonNode results = input.get("matches");
        // Looping until the "}" toke is found.
        for (final JsonNode element : results) {
            // Extracting the filed from the underlying JsonNode.
            JsonNode fieldname = element.get("translation");
            // Refining the translations, numbers and special characters must be eliminated
            // also the string must contain only lower case characters in order to ensure a
            // proper match between the word submitted by the user and the translation.
            translations.add(fieldname.toString().toLowerCase().replaceAll("[^a-zA-Z0\\u0020]", "")
                    .replaceAll("[0123456789]", ""));
        }
        // Returning all of possible the translations of the word passed by argument.
        return translations;
    }

    /**
     * The requestWords method extracts random lines from the dictionary and asks
     * for their translation via the getTranslation method.
     * 
     * @throws IOException if something fishy happens during the getTranslation
     *                     call.
     * @return an hashmap where the keys are the words and the values are the
     *         ArrayLists containing the words translation.
     */
    protected HashMap<String, ArrayList<String>> requestWords() throws IOException {
        // Initialize the ArrayList where the selected words will be insterted.
        ArrayList<String> selectedWords = new ArrayList<String>();
        // Initialize the HashMap where the selected words will be the keys and their
        // translations the values, selectedWords will be returned by the method.
        HashMap<String, ArrayList<String>> words = new HashMap<String, ArrayList<String>>();
        // Creating a random number generator.
        Random rand = new Random();
        // Randomizing the words.
        int i = 0;
        while (i < this.wordNum) {
            // Randomizing the words.
            String word = this.dictionary.get(rand.nextInt(this.dictionary.size()));
            if (!selectedWords.contains(word)) {
                selectedWords.add(word);
                i++;
            }
        }
        // Translating the words.
        for (String word : selectedWords) {
            ArrayList<String> translations = this.getTranslation(word);
            words.put(word, translations);
        }
        // Returning the words translations.
        return words;
    }
}