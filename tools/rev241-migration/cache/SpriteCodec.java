import java.io.ByteArrayOutputStream;
import java.util.*;

/**
 * Lossless sprite group codec (index 8), matching RuneLite 1.13.4's SpriteLoader layout:
 *   per frame: u8 flags (1 = stored column-major, 2 = has alpha), palette indexes, [alphas]
 *   palette: (n - 1) x 24-bit RGB (index 0 = transparent, not stored)
 *   footer: u16 maxWidth, u16 maxHeight, u8 n - 1, u16 offsetX[], u16 offsetY[], u16 width[], u16 height[], u16 frameCount
 * decode(encode(x)) and encode(decode(b)) == b for every group (verified over all three caches).
 */
public final class SpriteCodec {
	static final int FLAG_VERTICAL = 1, FLAG_ALPHA = 2;

	public static final class Frame {
		public int offsetX, offsetY, width, height, flags;
		public byte[] index;  // row-major palette indexes
		public byte[] alpha;  // row-major, only if FLAG_ALPHA
		/** canonical pixel value (rgb | alpha << 24, transparent = 0) for comparisons and re-palettising */
		public int[] argb(int[] palette) {
			int[] p = new int[width * height];
			for (int i = 0; i < p.length; i++) {
				int idx = index[i] & 0xff;
				int a = alpha != null ? alpha[i] & 0xff : (idx != 0 ? 0xff : 0);
				p[i] = idx == 0 && (alpha == null || a == 0) ? 0 : (palette[idx] & 0xffffff) | (a << 24);
			}
			return p;
		}
	}

	public static final class Group {
		public int maxWidth, maxHeight;
		public int[] palette; // [0] unused (transparent)
		public final List<Frame> frames = new ArrayList<>();
	}

	public static Group decode(byte[] b) {
		Group g = new Group();
		int n = ((b[b.length - 2] & 0xff) << 8) | (b[b.length - 1] & 0xff);
		int foot = b.length - 7 - n * 8;
		DefRewriter.In in = new DefRewriter.In(b);
		in.pos = foot;
		g.maxWidth = in.u16();
		g.maxHeight = in.u16();
		int palLen = in.u8() + 1;
		int[] ox = new int[n], oy = new int[n], w = new int[n], h = new int[n];
		for (int i = 0; i < n; i++) ox[i] = in.u16();
		for (int i = 0; i < n; i++) oy[i] = in.u16();
		for (int i = 0; i < n; i++) w[i] = in.u16();
		for (int i = 0; i < n; i++) h[i] = in.u16();
		in.pos = foot - (palLen - 1) * 3;
		g.palette = new int[palLen];
		for (int i = 1; i < palLen; i++) g.palette[i] = in.u24(); // raw value (the loader maps 0 -> 1 when drawing)
		in.pos = 0;
		for (int i = 0; i < n; i++) {
			Frame f = new Frame();
			f.offsetX = ox[i]; f.offsetY = oy[i]; f.width = w[i]; f.height = h[i];
			int dim = f.width * f.height;
			f.flags = in.u8();
			f.index = read(in, f.width, f.height, (f.flags & FLAG_VERTICAL) != 0);
			if ((f.flags & FLAG_ALPHA) != 0) f.alpha = read(in, f.width, f.height, (f.flags & FLAG_VERTICAL) != 0);
			if (f.index.length != dim) throw new IllegalStateException();
			g.frames.add(f);
		}
		if (in.pos != foot - (palLen - 1) * 3) throw new IllegalStateException("sprite data does not end at the palette");
		return g;
	}

	static byte[] read(DefRewriter.In in, int w, int h, boolean vertical) {
		byte[] out = new byte[w * h];
		if (!vertical) for (int j = 0; j < out.length; j++) out[j] = (byte) in.u8();
		else for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) out[w * y + x] = (byte) in.u8();
		return out;
	}

	static void write(ByteArrayOutputStream o, byte[] data, int w, int h, boolean vertical) {
		if (!vertical) o.write(data, 0, data.length);
		else for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) o.write(data[w * y + x]);
	}

	public static byte[] encode(Group g) {
		if (g.palette.length > 256) throw new IllegalStateException("palette has " + (g.palette.length - 1) + " colours (max 255)");
		ByteArrayOutputStream o = new ByteArrayOutputStream();
		for (Frame f : g.frames) {
			o.write(f.flags);
			write(o, f.index, f.width, f.height, (f.flags & FLAG_VERTICAL) != 0);
			if ((f.flags & FLAG_ALPHA) != 0) write(o, f.alpha, f.width, f.height, (f.flags & FLAG_VERTICAL) != 0);
		}
		for (int i = 1; i < g.palette.length; i++) { o.write(g.palette[i] >> 16); o.write(g.palette[i] >> 8); o.write(g.palette[i]); }
		o.write(g.maxWidth >> 8); o.write(g.maxWidth);
		o.write(g.maxHeight >> 8); o.write(g.maxHeight);
		o.write(g.palette.length - 1);
		for (Frame f : g.frames) { o.write(f.offsetX >> 8); o.write(f.offsetX); }
		for (Frame f : g.frames) { o.write(f.offsetY >> 8); o.write(f.offsetY); }
		for (Frame f : g.frames) { o.write(f.width >> 8); o.write(f.width); }
		for (Frame f : g.frames) { o.write(f.height >> 8); o.write(f.height); }
		o.write(g.frames.size() >> 8); o.write(g.frames.size());
		return o.toByteArray();
	}

	/** frame equality as drawn: geometry + pixels (palette layout ignored) */
	public static boolean sameFrame(Group ga, Frame a, Group gb, Frame b) {
		return a.offsetX == b.offsetX && a.offsetY == b.offsetY && a.width == b.width && a.height == b.height
			&& Arrays.equals(a.argb(ga.palette), b.argb(gb.palette));
	}

	/**
	 * Builds a group from frames of several source groups: one shared palette (<= 255 colours),
	 * every frame re-indexed into it. Frames keep their geometry and flags.
	 */
	public static Group assemble(int maxWidth, int maxHeight, List<Frame> frames, List<int[]> palettes) {
		Map<Integer, Integer> pal = new LinkedHashMap<>();
		List<Integer> colours = new ArrayList<>();
		colours.add(0);
		Group g = new Group();
		g.maxWidth = maxWidth;
		g.maxHeight = maxHeight;
		for (int fi = 0; fi < frames.size(); fi++) {
			Frame src = frames.get(fi);
			int[] p = palettes.get(fi);
			Frame f = new Frame();
			f.offsetX = src.offsetX; f.offsetY = src.offsetY; f.width = src.width; f.height = src.height; f.flags = src.flags;
			f.alpha = src.alpha == null ? null : src.alpha.clone();
			f.index = new byte[src.index.length];
			for (int i = 0; i < src.index.length; i++) {
				int idx = src.index[i] & 0xff;
				if (idx == 0) continue;
				int rgb = p[idx];
				Integer n = pal.get(rgb);
				if (n == null) { n = colours.size(); colours.add(rgb); pal.put(rgb, n); }
				f.index[i] = (byte) (int) n;
			}
			g.frames.add(f);
		}
		g.palette = colours.stream().mapToInt(Integer::intValue).toArray();
		return g;
	}
}
