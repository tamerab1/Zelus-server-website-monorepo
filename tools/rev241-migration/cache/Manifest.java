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

	/** colliding id the manifest keeps because it is identical to vanilla 241 (Jagex content imported early) */
	public boolean isImported(String kind, int id) {
		var t = json.getAsJsonObject("types").getAsJsonObject(kind);
		if (t == null) return false;
		var r = t.getAsJsonObject("reuse").get(String.valueOf(id));
		return r != null && (r.getAsString().startsWith("imported") || r.getAsString().equals("same bytes as vanilla 241 model " + id));
	}

	public boolean moves(String kind, int id) {
		Map<Integer, Integer> m = maps.get(kind);
		return m != null && m.containsKey(id) && m.get(id).intValue() != id;
	}
}
