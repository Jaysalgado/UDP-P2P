package p2p;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileReader;
import java.io.IOException;

public class ConfigHandler {
    public static void main(String[] args) {
        {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            try (FileReader reader = new FileReader("config.json")) {
                Config config = gson.fromJson(reader, Config.class);

                for (Config.Node node : config.getNodes()) {
                    System.out.println("Node " + node.getId() + ": " + node.getIp() + ":" + node.getPort());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
