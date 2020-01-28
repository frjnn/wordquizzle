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
 * The WQWord class implements the random retrieval of the specified number of
 * words to be fed to two players during a match. It also takes care of
 * translating the randomly choosen words by sending GET requests to the
 * MyMemory API.
 */
public class WQWords {

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
     * Returns a new WQWords object and populates the {@code dictionary} ArrayList
     * used to extract the match words.
     * 
     * @param wordNum the number of words to request.
     */
    public WQWords(int wordNum) {
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
     * @throws IOException if some problems occur during file opening.
     */
    private ArrayList<String> getTranslation(String word) throws IOException {
        ArrayList<String> translations = new ArrayList<String>();
        // The GET request.
        String HTTPrequest = "https://api.mymemory.translated.net/get?q=" + word.replace(" ", "%20")
                + "&langpair=it|en";
        JsonFactory jfactory = new JsonFactory();
        JsonParser jParser = jfactory.createParser(new URL(HTTPrequest));
        ObjectMapper mapper = new ObjectMapper();
        JsonNode input = mapper.readTree(jParser);
        final JsonNode results = input.get("matches");
        // loop until token equal to "}"
        for (final JsonNode element : results) {
            JsonNode fieldname = element.get("translation");
            translations.add(fieldname.toString().toLowerCase().replaceAll("[^a-zA-Z0\\u0020]", "")
                    .replaceAll("[0123456789]", ""));
        }
        return translations;
    }

    /**
     * The requestWords method extracts random lines from the dictionary and asks
     * for their translation via the getTranslation method.
     * 
     * @throws IOException if something wrong happens during the getTranslation
     *                     call.
     * @return an hashmap where the keys are the words and the values are the
     *         ArrayList containing the words translation.
     */
    protected HashMap<String, ArrayList<String>> requestWords() throws IOException {
        ArrayList<String> selectedWords = new ArrayList<String>();
        HashMap<String, ArrayList<String>> words = new HashMap<String, ArrayList<String>>();
        Random rand = new Random();
        int i = 0;
        while (i < this.wordNum) {
            String word = this.dictionary.get(rand.nextInt(this.dictionary.size()));
            if (!selectedWords.contains(word)) {
                selectedWords.add(word);
                i++;
            }
        }
        for (String word : selectedWords) {
            ArrayList<String> translations = this.getTranslation(word);
            words.put(word, translations);
        }
        return words;
    }
}