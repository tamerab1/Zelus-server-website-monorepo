import java.io.File;
import java.nio.file.Files;

// Read-only. Same loc-list decoding as DecodeCathedralLocs, but this time keeps the position and
// object "type" (attributes >> 2 -- 0-3 and 9 are wall pieces per Zelus's own Region.java loc
// decode, io.ruin.model.map.Region#load) instead of throwing it away, and prints only wall-type
// locs as absolute world coordinates near the Mad Angel arena anchor. Purpose: find the REAL wall
// layout around her spawn from the actual cache data, instead of guessing a bounding box for the
// movement-clamp fix -- exactly the "check the source/cache, don't guess" standard this project
// holds to.
//
// CATHEDRAL_REGION_ID = 10018 -> baseX = (10018>>8)*64 = 2496, baseY = (10018&0xFF)*64 = 2176
// (matches MadAngelIds.ARENA_ANCHOR_X/Y = 2532,2215 -> localX=36, localY=39, both in [0,64)).
public class DecodeCathedralWalls {
    static final int BASE_X = 2496;
    static final int BASE_Y = 2176;
    static final int ANCHOR_X = 2532;
    static final int ANCHOR_Y = 2215;
    static final int RADIUS = 45;

    public static void main(String[] args) throws Exception {
        byte[] data = Files.readAllBytes(new File(args[0]).toPath());
        InputStream in = new InputStream(data);

        int objectId = -1;
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
                int height = position >> 12;

                int attributes = in.readUnsignedByte();
                int type = attributes >> 2;
                int direction = attributes & 0x3;

                int absX = BASE_X + localX;
                int absY = BASE_Y + localY;

                if (Math.abs(absX - ANCHOR_X) > RADIUS || Math.abs(absY - ANCHOR_Y) > RADIUS) continue;
                boolean isWall = type <= 3 || type == 9;
                if (!isWall) continue;

                System.out.println("obj=" + objectId + " abs=(" + absX + "," + absY + ") h=" + height
                        + " type=" + type + " dir=" + direction
                        + " dx=" + (absX - ANCHOR_X) + " dy=" + (absY - ANCHOR_Y));
            }
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
