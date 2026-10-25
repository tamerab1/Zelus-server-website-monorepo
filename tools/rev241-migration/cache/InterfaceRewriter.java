import java.util.ArrayList;
import java.util.List;

/**
 * Byte-level rewriter for interface components (index 3 files), ported from RuneLite 1.13.4's
 * InterfaceLoader. Two component formats: IF3 (first byte 0xFF) and IF1 (legacy).
 *
 * Model ids in type-6 components were u16 before rev 237 and are i32 since (the loader keys this
 * off the interface index revision, > 1773137432). Rev 231 components are read with u16 models
 * and written with i32 models for rev 241.
 *
 * Remapped: model / animation / texture ids, the script id that starts every event hook,
 * varp (var transmit) and inv (inv transmit) trigger ids. Other int hook arguments are untyped,
 * so they are only reported when they equal an id that moves (see {@link #suspicious}).
 */
public final class InterfaceRewriter {
	private final DefRewriter.In in;
	private final DefRewriter.Out out = new DefRewriter.Out();
	private final DefRewriter.Ids ids;
	private final boolean inWideModels, outWideModels;
	/** "hook <name> arg <i> = <value> (kinds)" for int args equal to a moving id */
	public final List<String> suspicious = new ArrayList<>();
	private final java.util.function.Predicate<Integer> moving;
	/** Remaps component references (full ids iface << 16 | child) in hook args; and child indexes of parents. */
	public java.util.function.IntUnaryOperator componentIds = x -> x;
	private int group = -1;
	/** IF3 output offsets, for field patches: parent (u16), hidden (u8), click mask (u24), actions block */
	public int parentPos = -1, hiddenPos = -1, clickMaskPos = -1, actionsStart = -1, actionsEnd = -1;

	private InterfaceRewriter(byte[] b, DefRewriter.Ids ids, boolean inWide, boolean outWide, java.util.function.Predicate<Integer> moving) {
		this.in = new DefRewriter.In(b);
		this.ids = ids;
		this.inWideModels = inWide;
		this.outWideModels = outWide;
		this.moving = moving;
	}

	public static InterfaceRewriter run(byte[] b, DefRewriter.Ids ids, boolean inWide, boolean outWide, java.util.function.Predicate<Integer> moving) {
		return run(b, ids, inWide, outWide, moving, -1, x -> x);
	}

	/** group/componentIds: renumbered components of this interface (full ids) */
	public static InterfaceRewriter run(byte[] b, DefRewriter.Ids ids, boolean inWide, boolean outWide, java.util.function.Predicate<Integer> moving,
		int group, java.util.function.IntUnaryOperator componentIds) {
		InterfaceRewriter r = new InterfaceRewriter(b, ids, inWide, outWide, moving);
		r.group = group;
		r.componentIds = componentIds;
		if ((b[0] & 0xff) == 0xff) r.if3(); else r.if1();
		if (r.in.pos != b.length) {
			// a few vanilla IF3 model components carry 2 bytes of a newer optional field that neither
			// this grammar nor RuneLite's loader reads: copy them through untouched
			if ((b[0] & 0xff) == 0xff && (b[1] & 0xff) == 6 && b.length - r.in.pos <= 2) r.copy(b.length - r.in.pos);
			else throw new IllegalStateException("interface component: " + (b.length - r.in.pos) + " bytes left over");
		}
		return r;
	}

	public byte[] bytes() { return out.toByteArray(); }

	/** IF3: returns component bytes with the hidden flag set. */
	public static byte[] withHidden(byte[] comp, boolean hidden) {
		InterfaceRewriter r = run(comp, DefRewriter.IDENTITY, true, true, v -> false);
		byte[] o = comp.clone();
		o[r.hiddenPos] = (byte) (hidden ? 1 : 0);
		return o;
	}

	/** IF3: returns component bytes with option slot (0-based) set to text and its click-mask bit set. */
	public static byte[] withAction(byte[] comp, int slot, String text) {
		InterfaceRewriter r = run(comp, DefRewriter.IDENTITY, true, true, v -> false);
		List<String> actions = new ArrayList<>();
		DefRewriter.In in = new DefRewriter.In(comp);
		in.pos = r.actionsStart;
		int n = in.u8();
		for (int i = 0; i < n; i++) {
			StringBuilder sb = new StringBuilder();
			for (int c; (c = in.u8()) != 0; ) sb.append((char) c);
			actions.add(sb.toString());
		}
		while (actions.size() <= slot) actions.add("");
		actions.set(slot, text);
		int mask = ((comp[r.clickMaskPos] & 0xff) << 16) | ((comp[r.clickMaskPos + 1] & 0xff) << 8) | (comp[r.clickMaskPos + 2] & 0xff);
		mask |= 1 << (slot + 1);
		java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
		o.write(comp, 0, r.clickMaskPos);
		o.write(mask >> 16); o.write(mask >> 8); o.write(mask);
		o.write(comp, r.clickMaskPos + 3, r.actionsStart - (r.clickMaskPos + 3));
		o.write(actions.size());
		for (String a : actions) { o.writeBytes(a.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)); o.write(0); }
		o.write(comp, r.actionsEnd, comp.length - r.actionsEnd);
		return o.toByteArray();
	}

	// ---------------------------------------------------------------- helpers

	private int u8() { int v = in.u8(); out.u8(v); return v; }
	private int u16() { int v = in.u16(); out.u16(v); return v; }
	void copy(int n) { for (int i = 0; i < n; i++) out.u8(in.u8()); }
	private void string() { int c; do { c = in.u8(); out.u8(c); } while (c != 0); }

	private void id16(String kind) {
		int v = in.u16();
		out.u16(v == 0xFFFF ? v : ids.map(kind, v));
	}

	private void model() {
		int v = inWideModels ? in.i32() : in.u16();
		if (!inWideModels && v == 0xFFFF) v = -1;
		if (v != -1) v = ids.map("model", v);
		if (outWideModels) out.i32(v);
		else out.u16(v == -1 ? 0xFFFF : v);
	}

	// ---------------------------------------------------------------- IF3

	static final String[] HOOKS = {"onLoad", "onMouseOver", "onMouseLeave", "onTargetLeave", "onTargetEnter", "onVarTransmit",
		"onInvTransmit", "onStatTransmit", "onTimer", "onOp", "onMouseRepeat", "onClick", "onClickRepeat", "onRelease",
		"onHold", "onDrag", "onDragComplete", "onScrollWheel"};

	private void if3() {
		u8(); // 0xFF marker
		int type = u8();
		copy(2);             // content type
		copy(4);             // x, y
		copy(2);             // width
		copy(2);             // height (signed for type 9, same width)
		int widthMode = (byte) u8();
		int heightMode = (byte) u8();
		copy(2);             // position modes
		parentPos = out.size();
		int parent = in.u16();
		if (parent != 0xFFFF && group >= 0) parent = componentIds.applyAsInt(group << 16 | parent) & 0xFFFF;
		out.u16(parent);
		hiddenPos = out.size();
		copy(1);             // hidden
		if (type == 0) copy(5);
		if (type == 5) {
			copy(4);         // sprite (custom sprites don't collide)
			id16("texture");
			copy(1 + 1 + 1 + 4 + 1 + 1);
		}
		if (type == 6) {
			model();
			copy(2 + 2 + 2 + 2 + 2 + 2); // offsets, rotations, zoom
			id16("seq");
			copy(1);         // orthogonal
			copy(2);
			if (widthMode != 0) copy(2);
			if (heightMode != 0) copy(2);
		}
		if (type == 4) {
			copy(2);         // font
			string();
			copy(1 + 1 + 1 + 1 + 4);
		}
		if (type == 3) copy(4 + 1 + 1);
		if (type == 9) copy(1 + 4 + 1);
		clickMaskPos = out.size();
		copy(3);             // click mask
		string();            // name
		actionsStart = out.size();
		int ops = u8();
		for (int i = 0; i < ops; i++) string();
		actionsEnd = out.size();
		copy(3);             // drag dead zone/time, render behaviour
		string();            // target verb
		for (String hook : HOOKS) listener(hook);
		triggers("varp");
		triggers("inv");
		triggers(null);      // stats
	}

	private void listener(String hook) {
		int n = u8();
		for (int i = 0; i < n; i++) {
			int t = u8();
			if (t == 0) {
				int v = in.i32();
				if (i == 0) v = ids.map("clientscript", v);
				else {
					int c = componentIds.applyAsInt(v);
					if (c != v) v = c;
					else if (moving.test(v)) suspicious.add(hook + " arg " + i + " = " + v);
				}
				out.i32(v);
			} else if (t == 1) string();
			else throw new IllegalStateException("bad hook arg type " + t);
		}
	}

	private void triggers(String kind) {
		int n = u8();
		for (int i = 0; i < n; i++) {
			int v = in.i32();
			out.i32(kind == null ? v : ids.map(kind, v));
		}
	}

	// ---------------------------------------------------------------- IF1

	private void if1() {
		int type = u8();
		int menuType = u8();
		copy(2 + 2 + 2 + 2 + 2 + 1); // content type, x, y, width, height, opacity
		copy(2);             // parent
		copy(2);             // hovered sibling
		int alts = u8();
		copy(alts * 3);
		int scripts = u8();
		for (int s = 0; s < scripts; s++) {
			int n = u16();
			copy(n * 2);     // legacy cs1 bytecode (vanilla only; no custom ids)
		}
		if (type == 0) copy(2 + 1);
		if (type == 1) copy(2 + 1);
		if (type == 2) {
			copy(4);         // inventory flags
			copy(2);         // x/y pitch
			for (int i = 0; i < 20; i++) if (u8() == 1) copy(2 + 2 + 4);
			for (int i = 0; i < 5; i++) string();
		}
		if (type == 3) copy(1);
		if (type == 4 || type == 1) copy(1 + 1 + 1 + 2 + 1);
		if (type == 4) { string(); string(); }
		if (type == 1 || type == 3 || type == 4) copy(4);
		if (type == 3 || type == 4) copy(12);
		if (type == 5) copy(8);
		if (type == 6) {
			model();         // model
			model();         // alternate model
			id16("seq");
			id16("seq");
			copy(6);         // zoom, rotations
		}
		if (type == 7) {
			copy(1 + 2 + 1 + 4 + 2 + 2);
			copy(1);
			for (int i = 0; i < 5; i++) string();
		}
		if (type == 8) string();
		if (menuType == 2 || type == 2) { string(); string(); copy(2); }
		if (menuType == 1 || menuType == 4 || menuType == 5 || menuType == 6) string();
	}
}
