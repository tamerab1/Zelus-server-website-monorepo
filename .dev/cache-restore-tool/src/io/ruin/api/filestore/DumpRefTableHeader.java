package io.ruin.api.filestore;

public class DumpRefTableHeader {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        FileStore fs = new FileStore(cachePath);
        IndexFile index255 = fs.get(255);
        byte[] raw = index255.getArchiveData(7); // MODELS index's own reference-table entry
        System.out.println("raw archiveData length (container) = " + raw.length);
        byte[] decompressed = Archive.decompress(raw, null);
        System.out.println("decompressed length = " + decompressed.length);
        System.out.println("protocol byte = " + (decompressed[0] & 0xFF));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(20, decompressed.length); i++) sb.append(decompressed[i] & 0xFF).append(" ");
        System.out.println("first 20 bytes = " + sb);
    }
}
