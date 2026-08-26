package player.mongo;

import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;
import io.ruin.services.MongoPlayerMirror;
import io.ruin.services.MongoPlayerMirrorRegistry;
import org.bson.Document;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Uses the RAW/untyped `Document` view of the collection exclusively (never the typed
 * Connection.collection(), which decodes through a custom Player/Mongo codec whose Map-field
 * decode() was never implemented -- this mirror was write-only until this class, so nothing had
 * ever exercised that read path before and it throws IllegalStateException("unimplemented").
 * Document's own default BSON decoding sidesteps that broken codec entirely.
 */
public class MongoPlayerMirrorImpl implements MongoPlayerMirror {

	public static void register() {
		MongoPlayerMirrorRegistry.register(new MongoPlayerMirrorImpl());
	}

	private static List<Document> fetchAll() {
		List<Document> docs = Flux.from(Connection.rawCollection().find()).collectList().block();
		return docs == null ? List.of() : docs;
	}

	@Override
	public List<String> allNames() {
		return fetchAll().stream().map(d -> d.getString("name")).collect(Collectors.toList());
	}

	@Override
	public int dumpAll(Path dest) {
		List<Document> docs = fetchAll();
		List<String> encoded = docs.stream().map(Document::toJson).collect(Collectors.toList());
		try {
			Files.writeString(dest, "[" + String.join(",\n", encoded) + "]");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return docs.size();
	}

	@Override
	public int deleteAllExcept(Set<String> preservedNormalized, Function<String, String> normalize) {
		List<Document> docs = fetchAll();
		List<String> toDeleteUuids = docs.stream()
				.filter(d -> !preservedNormalized.contains(normalize.apply(d.getString("name"))))
				.map(d -> d.getString("uuid"))
				.collect(Collectors.toList());
		if (toDeleteUuids.isEmpty()) {
			return 0;
		}
		var result = Connection.rawCollection().deleteMany(Filters.in("uuid", toDeleteUuids));
		Long deleted = Mono.from(result).map(DeleteResult::getDeletedCount).block();
		return deleted == null ? 0 : deleted.intValue();
	}
}
