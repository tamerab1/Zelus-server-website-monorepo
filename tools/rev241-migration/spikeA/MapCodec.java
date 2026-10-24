import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

// Hand-written terrain + loc codec for the classic opcode-stream format, used to test whether
// rev 241 region groups (index 5, group = regionId, file 0 = terrain, file 1 = locs) still use
// the same grammar as rev 231's mX_Y / lX_Y files. decode() must consume every byte exactly,
// and encode(decode(b)) must equal b, for a format to count as understood.
public class MapCodec {

	// ---------- terrain ----------
	public static final class Tile {
		public int height = -1;          // -1 = no explicit height (opcode 0, procedural)
		public int overlay = -1, shape, rotation;
		public int settings = -1;
		public int underlay = -1;
		public final List<Integer> order = new ArrayList<>(); // 'O','S','U' in stream order
	}

	// 231 terrain = 4 planes of 64x64 tiles. 241 terrain = the same 4 planes, then a u8 count of
	// extra 64x64 layers in the same tile grammar (0 in 2948/2949 vanilla regions, 1 in 16457).
	public static final class Terrain {
		public Tile[][][] planes;
		public final List<Tile[][]> extraLayers = new ArrayList<>();
		public boolean hasTrailer;
	}

	private static Tile readTile(Buf in) {
		Tile tile = new Tile();
		while (true) {
			int op = in.u16();
			if (op == 0) break;
			if (op == 1) { tile.height = in.u8(); break; }
			if (op <= 49) { tile.overlay = in.s16() & 0xffff; tile.shape = (op - 2) / 4; tile.rotation = (op - 2) & 3; tile.order.add((int) 'O'); }
			else if (op <= 81) { tile.settings = op - 49; tile.order.add((int) 'S'); }
			else { tile.underlay = op - 81; tile.order.add((int) 'U'); }
		}
		return tile;
	}

	private static void writeTile(Out out, Tile tile) {
		for (int o : tile.order) {
			if (o == 'O') { out.u16(2 + (tile.shape << 2) + tile.rotation); out.u16(tile.overlay); }
			else if (o == 'S') out.u16(49 + tile.settings);
			else out.u16(81 + tile.underlay);
		}
		if (tile.height >= 0) { out.u16(1); out.u8(tile.height); } else out.u16(0);
	}

	// format231 = no trailer allowed; otherwise the 241 trailer is required.
	public static Terrain decodeTerrain(byte[] b, boolean format231) {
		Buf in = new Buf(b);
		Terrain t = new Terrain();
		t.planes = new Tile[4][64][64];
		for (int z = 0; z < 4; z++)
			for (int x = 0; x < 64; x++)
				for (int y = 0; y < 64; y++)
					t.planes[z][x][y] = readTile(in);
		if (!format231) {
			t.hasTrailer = true;
			int layers = in.u8();
			for (int n = 0; n < layers; n++) {
				Tile[][] layer = new Tile[64][64];
				for (int x = 0; x < 64; x++)
					for (int y = 0; y < 64; y++)
						layer[x][y] = readTile(in);
				t.extraLayers.add(layer);
			}
		}
		if (in.pos != b.length) throw new IllegalStateException("terrain leftover " + (b.length - in.pos));
		return t;
	}

	public static byte[] encodeTerrain(Terrain t, boolean format241) {
		Out out = new Out();
		for (int z = 0; z < 4; z++)
			for (int x = 0; x < 64; x++)
				for (int y = 0; y < 64; y++)
					writeTile(out, t.planes[z][x][y]);
		if (format241) {
			out.u8(t.extraLayers.size());
			for (Tile[][] layer : t.extraLayers)
				for (int x = 0; x < 64; x++)
					for (int y = 0; y < 64; y++)
						writeTile(out, layer[x][y]);
		}
		return out.toByteArray();
	}

	// legacy helpers (231-only grammar)
	public static Tile[][][] decodeTerrain(byte[] b) { return decodeTerrain(b, true).planes; }

	public static byte[] encodeTerrain(Tile[][][] planes) {
		Terrain t = new Terrain();
		t.planes = planes;
		return encodeTerrain(t, false);
	}

	// ---------- locs ----------
	public static final class Loc {
		public int id, plane, x, y, type, rotation;
		public Loc(int id, int plane, int x, int y, int type, int rotation) { this.id = id; this.plane = plane; this.x = x; this.y = y; this.type = type; this.rotation = rotation; }
	}

	public static List<Loc> decodeLocs(byte[] b) {
		Buf in = new Buf(b);
		List<Loc> locs = new ArrayList<>();
		int id = -1;
		int idDelta;
		while ((idDelta = in.uIntSmartShortCompat()) != 0) {
			id += idDelta;
			int pos = 0, posDelta;
			while ((posDelta = in.uSmart()) != 0) {
				pos += posDelta - 1;
				int attr = in.u8();
				locs.add(new Loc(id, pos >> 12 & 3, pos >> 6 & 63, pos & 63, attr >> 2, attr & 3));
			}
		}
		if (in.pos != b.length) throw new IllegalStateException("locs leftover " + (b.length - in.pos));
		return locs;
	}

	public static byte[] encodeLocs(List<Loc> locs) {
		Out out = new Out();
		int lastId = -1, i = 0;
		while (i < locs.size()) {
			int id = locs.get(i).id;
			out.uIntSmartShortCompat(id - lastId);
			lastId = id;
			int lastPos = 0;
			for (; i < locs.size() && locs.get(i).id == id; i++) {
				Loc l = locs.get(i);
				int pos = l.plane << 12 | l.x << 6 | l.y;
				out.uSmart(pos - lastPos + 1);
				lastPos = pos;
				out.u8(l.type << 2 | l.rotation);
			}
			out.uSmart(0);
		}
		out.uIntSmartShortCompat(0);
		return out.toByteArray();
	}

	// ---------- buffers ----------
	static final class Buf {
		final byte[] b; int pos;
		Buf(byte[] b) { this.b = b; }
		int u8() { return b[pos++] & 0xff; }
		int u16() { return (u8() << 8) | u8(); }
		int s16() { return (short) u16(); }
		int uSmart() { int p = b[pos] & 0xff; return p < 128 ? u8() : u16() - 0x8000; }
		int uIntSmartShortCompat() {
			int total = 0, v = uSmart();
			while (v == 32767) { total += 32767; v = uSmart(); }
			return total + v;
		}
	}

	static final class Out extends ByteArrayOutputStream {
		void u8(int v) { write(v); }
		void u16(int v) { write(v >> 8); write(v); }
		void uSmart(int v) { if (v < 128) u8(v); else u16(v + 0x8000); }
		void uIntSmartShortCompat(int v) { while (v >= 32767) { uSmart(32767); v -= 32767; } uSmart(v); }
	}
}
