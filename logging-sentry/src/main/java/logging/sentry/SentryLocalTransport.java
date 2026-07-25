package logging.sentry;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import io.sentry.Hint;
import io.sentry.SentryEnvelope;
import io.sentry.transport.ITransport;
import io.sentry.transport.RateLimiter;

// when sentry used locally, we just save some data to files
public class SentryLocalTransport implements ITransport {

	// Mirrors io.ruin.api.utils.ServerWrapper.parseDataFolder() without
	// depending on kronos-api (that module pulls in HikariCP/MySQL/etc.,
	// far more than this needs just to resolve one path).
	private static Path resolveDataPath() {
		var properties = new Properties();
		try (var in = new FileInputStream("server.properties")) {
			properties.load(in);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return Path.of(properties.getProperty("data_path"));
	}

	@Override
	public void close() throws IOException {}

	@Override
	public void send(@NotNull SentryEnvelope envelope, @NotNull Hint hint) throws IOException {
		for (var item : envelope.getItems()) {
			try {
				if (!item.getHeader().getType().name().equalsIgnoreCase("transaction")) {
					return;
				}
				var data = item.getData();
				// Was a bare relative path ("data/runtime/logs/..."), resolved
				// against the JVM's working directory rather than the
				// configured data_path -- same class of bug fixed in
				// HWIDManager/RSProtNetworkSnapshotService. The missing
				// parent directory also meant Files.write threw
				// NoSuchFileException on every single call (CREATE only
				// creates the file, not missing parent dirs).
				var dir = resolveDataPath().resolve("runtime").resolve("logs");
				Files.createDirectories(dir);
				Files.write(dir.resolve("transaction.json"), data, StandardOpenOption.CREATE,
						StandardOpenOption.TRUNCATE_EXISTING);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void flush(long timeoutMillis) {}

	@Override
	public @Nullable RateLimiter getRateLimiter() {
		return null;
	}

	@Override
	public void close(boolean isRestarting) throws IOException {}


}
