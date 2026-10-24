import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** remap-241.json loaded as kind -> (old id -> new id). Ids not in a table keep their value. */
public final class Manifest implements DefRewriter.Ids {
	public final Map<String, Map<Integer, Integer>> maps = new HashMap<>();
	public final JsonObject json;

	public Manifest(Path file) throws Exception {
		json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		JsonObject types = json.getAsJsonObject("types");
		for (String kind : types.keySet()) {
			Map<Integer, Integer> m = new HashMap<>();
			for (var e : types.getAsJsonObject(kind).getAsJsonObject("map").entrySet()) {
				m.put(Integer.parseInt(e.getKey()), e.getValue().getAsInt());
			}
			maps.put(kind, m);
		}
	}

	@Override
	public int map(String kind, int id) {
		Map<Integer, Integer> m = maps.get(kind);
		return m == null ? id : m.getOrDefault(id, id);
	}

	public boolean moves(String kind, int id) {
		Map<Integer, Integer> m = maps.get(kind);
		return m != null && m.containsKey(id) && m.get(id) != id;
	}
}
