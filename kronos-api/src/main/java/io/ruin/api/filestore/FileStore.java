package io.ruin.api.filestore;

import io.ruin.api.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class FileStore {

	public final IndexFile index255;

	public final IndexFile[] files;

	public FileStore(String path) throws IOException {
		path = FileUtils.get(path).getAbsolutePath() + File.separator;
		RandomAccessFile mainDataFile = new RandomAccessFile(path + "main_file_cache.dat2", "rw");
		RandomAccessFile mainDataFile255 = new RandomAccessFile(path + "main_file_cache.idx255", "rw");

		// reference tables: rev 241's models table alone is 525,411 bytes (540,634 with ours), over the old
		// 500,000 cap, which left index 7 unloaded and unservable over JS5
		this.index255 = new IndexFile(255, mainDataFile, mainDataFile255, 10_000_000);
		this.files = new IndexFile[index255.getArchivesCount()];

		for (int index = 0; index < files.length; index++) {
			byte[] archiveData = index255.getArchiveData(index);
			if (archiveData == null) {
				continue;
			}
			RandomAccessFile indexDataFile = new RandomAccessFile(path + "main_file_cache.idx" + index, "rw");
			// 10 MB per archive: the old 1,000,000 cap refused real archives (sprites 17014 / 17015 are 1,026,075 bytes),
			// so JS5 dropped those requests and a client doing a full cache download waited on them forever
			IndexFile indexFile = new IndexFile(index, mainDataFile, indexDataFile, 10_000_000);
			indexFile.setReferenceTable(new ReferenceTable(archiveData));
			files[index] = indexFile;
		}
	}

	public void reloadReferenceTable() {
		for (int index = 0; index < files.length; index++) {
			byte[] archiveData = index255.getArchiveData(index);
			if (archiveData == null) {
				System.err.println("Failed to read archive data for index: " + index);
				continue;
			}

			files[index].setReferenceTable(new ReferenceTable(archiveData));
		}
	}

	public IndexFile get(int index) {
		if (index < 0)
			return null;
		if (index == 255)
			return index255;
		if (index >= files.length)
			return null;
		return files[index];
	}

}
