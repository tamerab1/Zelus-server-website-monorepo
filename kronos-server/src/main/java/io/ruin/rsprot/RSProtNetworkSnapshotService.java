package io.ruin.rsprot;

import io.ruin.api.utils.ServerWrapper;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.rsprot.protocol.api.traffic.ConcurrentNetworkTrafficWriter;
import net.rsprot.protocol.loginprot.incoming.util.LoginBlock;
import net.rsprot.protocol.metrics.snapshots.impl.ConcurrentNetworkTrafficSnapshot;

@Slf4j
public class RSProtNetworkSnapshotService {
	private static final ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor(r -> {
		var thread = new Thread(r, "network-snapshot");
		thread.setDaemon(true);
		return thread;
	});

	public static void start() {
		service.scheduleAtFixedRate(RSProtNetworkSnapshotService::save, 5, 5, TimeUnit.MINUTES);
	}

	private static void save() {
		var monitor = RSProtService.service().getTrafficMonitor();
		@SuppressWarnings("unchecked")
		var snapshot = (ConcurrentNetworkTrafficSnapshot<LoginBlock<?>>) monitor.resetTransient();
		var data = ConcurrentNetworkTrafficWriter.INSTANCE.write(snapshot);
		// Was a bare relative path ("data/runtime/network/"), resolved
		// against the JVM's working directory (/app in the container)
		// rather than the configured data_path -- even where mkdirs()
		// succeeded this would be ephemeral container storage, lost on
		// every redeploy, not the persistent /data mount. Matched to the
		// same ServerWrapper.dataFolder convention everything else uses.
		var dir = new File(ServerWrapper.dataFolder, "runtime/network/");
		if (!dir.exists()) {
			dir.mkdirs();
		}

		try {
			Files.write(dir.toPath().resolve(new Date().toString().replace(":", "-") + ".txt"), data.getBytes(), StandardOpenOption.CREATE_NEW);
		} catch (Exception e) {
			log.error("Unable to write net log.", e);
		}
	}
}
