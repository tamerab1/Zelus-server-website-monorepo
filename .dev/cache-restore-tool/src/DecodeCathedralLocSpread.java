import java.io.File;
import java.nio.file.Files;

// Read-only, one-off: what is the full x/y SPREAD of every loc in the Cathedral's own mapsquare
// (39,34), not just near the arena anchor? Answers: is there real scenery immediately outside the
// hall's own walls (within this SAME already-imported mapsquare), or is the area around the
// building genuinely blank/empty in the source data itself?
public class DecodeCathedralLocSpread {
    static final int BASE_X = 2496;
    static final int BASE_Y = 2176;

    public static void main(String[] args) throws Exception {
        byte[] data = Files.readAllBytes(new File(args[0]).toPath());
        InputStream in = new InputStream(data);

        int objectId = -1;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int count = 0;
        // Histogram: how many locs per 8x8-tile "chunk" across the 64x64 mapsquare.
        int[][] chunkCounts = new int[8][8];

        for (;;) {
            int idOffset = readUnsignedIntSmartShortCompat(in);
            if (idOffset == 0) break;
            objectId += idOffset;

            int position = 0;
            for (;;) {
                int positionOffset = readUnsignedIntSmartShortCompat(in);
                if (positionOffset == 0) break;
                position += positionOffset - 1;
                int localY = position & 0x3f;
                int localX = (position >> 6) & 0x3f;

                in.readUnsignedByte(); // attributes

                int absX = BASE_X + localX;
                int absY = BASE_Y + localY;
                minX = Math.min(minX, absX); maxX = Math.max(maxX, absX);
                minY = Math.min(minY, absY); maxY = Math.max(maxY, absY);
                count++;
                chunkCounts[localX / 8][localY / 8]++;
            }
        }

        System.out.println("total loc placements: " + count);
        System.out.println("bounding box: x[" + minX + "," + maxX + "] y[" + minY + "," + maxY + "]");
        System.out.println("(mapsquare spans x[" + BASE_X + "," + (BASE_X + 63) + "] y[" + BASE_Y + "," + (BASE_Y + 63) + "])");
        System.out.println();
        System.out.println("loc count per 8x8 chunk (rows = Y chunk 7..0 north to south, cols = X chunk 0..7 west to east):");
        for (int cy = 7; cy >= 0; cy--) {
            StringBuilder row = new StringBuilder();
            for (int cx = 0; cx < 8; cx++) {
                row.append(String.format("%5d", chunkCounts[cx][cy]));
            }
            System.out.println("y-chunk " + cy + " (abs y " + (BASE_Y + cy * 8) + "-" + (BASE_Y + cy * 8 + 7) + "): " + row);
        }
    }

    static int readUnsignedIntSmartShortCompat(InputStream in) {
        int value = 0;
        int part;
        do {
            part = readUSmart(in);
            value += part;
        } while (part == 32767);
        return value;
    }

    static int readUSmart(InputStream in) {
        int peek = in.peek() & 0xFF;
        return peek < 128 ? in.readUnsignedByte() : in.readUnsignedShort() - 0x8000;
    }

    static class InputStream {
        byte[] data;
        int pos = 0;
        InputStream(byte[] data) { this.data = data; }
        int peek() { return data[pos] & 0xFF; }
        int readUnsignedByte() { return data[pos++] & 0xFF; }
        int readUnsignedShort() { int v = ((data[pos] & 0xFF) << 8) | (data[pos+1] & 0xFF); pos += 2; return v; }
    }
}
