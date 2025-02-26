package p2p;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileReader;
import java.io.IOException;

public class ConfigHandler {
    public static Config loadConfig() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        try (FileReader readingFile = new FileReader("src/config.json")) {
            return gson.fromJson(readingFile, Config.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
