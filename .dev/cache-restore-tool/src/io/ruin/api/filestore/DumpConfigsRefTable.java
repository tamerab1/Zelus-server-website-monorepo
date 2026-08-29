package io.ruin.api.filestore;

public class DumpConfigsRefTable {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        FileStore fs = new FileStore(cachePath);
        IndexFile index255 = fs.get(255);
        byte[] raw = index255.getArchiveData(2); // CONFIGS index's own reference-table entry
        System.out.println("raw archiveData length (container) = " + raw.length);
        byte[] decompressed = Archive.decompress(raw, null);
        System.out.println("decompressed length = " + decompressed.length);
        System.out.println("protocol byte = " + (decompressed[0] & 0xFF));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(30, decompressed.length); i++) sb.append(decompressed[i] & 0xFF).append(" ");
        System.out.println("first 30 bytes = " + sb);
    }
}
