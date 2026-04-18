import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

public class TextBank {
    private final Map<String, ArrayList<String>> textMap;
    private final Random random;

    public TextBank(String fileName) {
        textMap = new HashMap<>();
        random = new Random();
        load(fileName);
    }

    private void load(String fileName) {
        try {
            Scanner fileScanner = new Scanner(new File(fileName));

            while (fileScanner.hasNextLine()) {
                String line = fileScanner.nextLine().trim();

                if (line.length() == 0 || line.startsWith("#")) {
                    continue;
                }

                int equalsIndex = line.indexOf('=');
                if (equalsIndex == -1) {
                    continue;
                }

                String key = line.substring(0, equalsIndex).trim();
                String value = line.substring(equalsIndex + 1).trim();

                if (!textMap.containsKey(key)) {
                    textMap.put(key, new ArrayList<String>());
                }
                textMap.get(key).add(value);
            }

            fileScanner.close();
        } catch (FileNotFoundException e) {
            System.out.println("Could not find phrases file: " + fileName);
        }
    }

    public String get(String key) {
        if (!textMap.containsKey(key) || textMap.get(key).size() == 0) {
            return "[Missing text: " + key + "]";
        }
        return textMap.get(key).get(0);
    }

    public String getRandom(String key) {
        if (!textMap.containsKey(key) || textMap.get(key).size() == 0) {
            return "[Missing text: " + key + "]";
        }

        ArrayList<String> options = textMap.get(key);
        int index = random.nextInt(options.size());
        return options.get(index);
    }
}