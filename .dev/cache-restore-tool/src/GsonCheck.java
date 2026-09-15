import com.google.gson.*;
import java.io.*;
import java.nio.file.*;

public class GsonCheck {
    public static void main(String[] args) throws Exception {
        String content = new String(Files.readAllBytes(Paths.get(args[0])), "UTF-8");
        Gson gson = new Gson();
        JsonArray arr = gson.fromJson(content, JsonArray.class);
        System.out.println("Parsed OK, total entries: " + arr.size());
        int found = 0;
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("id")) {
                int id = o.get("id").getAsInt();
                if (id >= 60268 && id <= 60282) {
                    found++;
                    System.out.println(id + " -> " + o.get("name").getAsString());
                }
            }
        }
        System.out.println("new custom entries found: " + found);
    }
}
