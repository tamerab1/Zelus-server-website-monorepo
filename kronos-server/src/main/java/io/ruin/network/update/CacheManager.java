package io.ruin.network.update;

import io.ruin.Server;
import io.ruin.api.buffer.OutBuffer;
import io.ruin.api.filestore.FileStore;
import io.ruin.api.filestore.IndexFile;

import java.io.IOException;
import java.util.HashMap;

public final class CacheManager {

	private static final HashMap<Long, OutBuffer> cachedBuffers = new HashMap<>(1000);

	/**
	 * Sentinel for a JS5 request that resolved to no data (missing/corrupt archive on disk).
	 * Without this, a client that keeps retrying a request it never gets a response for (normal
	 * JS5 client behaviour) re-does the disk read AND re-enters this method's global lock on
	 * every single retry -- for every player, since get() is synchronized across all connections.
	 * A handful of permanently-broken archives being hammered by one client's retry loop was
	 * enough to stall JS5 serving for everyone else too (see teleport/login freeze investigation).
	 */
	private static final OutBuffer NULL_MARKER = new OutBuffer(0);

	public static synchronized OutBuffer get(int index, int archiveId, boolean priority) throws IOException {
		int storedType = priority ? index : index + 256;
		long hash = (long) archiveId + ((long) storedType << 32);
		FileStore fs = Server.fileStore;
		HashMap<Long, OutBuffer> map = cachedBuffers;
		OutBuffer cached = map.get(hash);
		if (cached != null) {
			return cached == NULL_MARKER ? null : cached;
		}
		if (index == 255 && archiveId == 255) {
			OutBuffer out = new OutBuffer(fs.files.length * 8);
			for (IndexFile file : fs.files) {
				if (file == null) {
					out.addInt(0);
					out.addInt(0);
					continue;
				}
				out.addInt(file.table.crc);
				out.addInt(file.table.revision);
			}
			byte[] archive = out.toByteArray();
			OutBuffer buffer = new OutBuffer(8 + archive.length);
			buffer.addByte(index);
			buffer.addShort(archiveId);
			buffer.addByte(0);
			buffer.addInt(archive.length);
			int offset = 8;
			for (byte b : archive) {
				if (offset == 512) {
					buffer.addByte(255);
					offset = 1;
				}
				buffer.addByte(b);
				++offset;
			}
			cached = buffer;
		} else {
			IndexFile file = fs.get(index);
			if (file == null || (index == 255 ? archiveId >= fs.files.length : !file.archiveExists(archiveId))) {
				map.put(hash, NULL_MARKER);
				return null;
			}
			byte[] archive = file.getArchiveData(archiveId);
			if (archive == null) {
				map.put(hash, NULL_MARKER);
				return null;
			}
			int compression = archive[0] & 255;
			int length = ((archive[1] & 255) << 24) + ((archive[2] & 255) << 16) + ((archive[3] & 255) << 8) + (archive[4] & 255);
			int realLength = compression != 0 ? length + 4 : length;
			OutBuffer buffer = new OutBuffer(8 + archive.length);
			buffer.addByte(index);
			buffer.addShort(archiveId);
			buffer.addByte(compression);
			buffer.addInt(length);
			int offset = 8;
			for (int i = 5; i < realLength + 5; ++i) {
				if (offset == 512) {
					buffer.addByte(255);
					offset = 1;
				}
				buffer.addByte(archive[i]);
				++offset;
			}
			cached = buffer;
		}
		map.put(hash, cached);
		return cached;
	}
}

