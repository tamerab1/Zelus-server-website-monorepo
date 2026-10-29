import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;

/**
 * Stage 6 of the rev 241 cache build: spotanims the Fractured Archive (Raid 4) server code needs that vanilla
 * doesn't ship. Runs on the built cache241; idempotent.
 *
 *  - 6838: the Archive Guardian's thrown rock (vanilla 4201) without its skeletal sequence. Sent as a projectile,
 *    vanilla 4201's sequence (14761) draws the rock far off the flight path, high and to the side, with every
 *    renderer; the same model with no sequence flies exactly where the projectile is.
 *
 * args: cache241Dir
 */
public class PatchRaid4Spotanims {
	static final int SPOTANIM_GROUP = 13;
	static final int SEQUENCE_OPCODE = 2;

	/** new id -> vanilla source id (the copy drops the source's sequence). */
	static final Map<Integer, Integer> STATIC_COPIES = Map.of(6838, 4201);

	/**
	 * new id -> {vanilla source, sequence (-1 = the source's), find, replace, ...}: a recoloured copy. The Soulbound aura
	 * Eternal Soulfire (round 3, 2026-10-29: the testers wanted it in the family of Soul Wisps / Ascendant Radiance, the
	 * Redemption sparkle 436 / model 9249's four rings, but its own): two layers on the silent sparkle seq 19021 --
	 * 6839 a larger halo round the body, its rings alternating violet and cyan (the other auras are one colour), and
	 * 6840 a small white-cyan crown ring played at head height.
	 */
	static final Map<Integer, int[]> RECOLOURED_COPIES = Map.of(
			6839, new int[] { 436, 19021, 32879, 52206, 32997, 31716, 34021, 52186, 37093, 33631 },
			6840, new int[] { 436, 19021, 32879, 31743, 32997, 31733, 34021, 32734, 37093, 30700 });
	/** new id -> size (opcodes 4 / 5, 128 = as is). */
	static final Map<Integer, Integer> SCALES = Map.of(6839, 168, 6840, 84);

	/** new seq -> vanilla source: a copy without its frame sounds (opcode 14). 19020: the flare's 9543; 19021: the sparkle's 2596. */
	static final Map<Integer, Integer> SILENT_SEQS = Map.of(19020, 9543, 19021, 2596);
	static final int SEQUENCE_GROUP = 12;

	public static void main(String[] a) throws Exception {
		Store out = new Store(new File(a[0]));
		out.load();
		TreeMap<Integer, byte[]> files = new TreeMap<>(RoundTripDefs.files(out, SPOTANIM_GROUP));
		int written = 0;
		// the silent sequences first (their own config group)
		TreeMap<Integer, byte[]> seqs = new TreeMap<>(RoundTripDefs.files(out, SEQUENCE_GROUP));
		int seqsWritten = 0;
		for (var e : SILENT_SEQS.entrySet()) {
			byte[] source = seqs.get(e.getValue());
			if (source == null)
				throw new IllegalStateException("seq " + e.getValue() + " missing from the cache");
			byte[] copy = withoutOpcode("seq", source, 14);
			byte[] existing = seqs.get(e.getKey());
			if (existing != null) {
				if (Arrays.equals(existing, copy))
					continue;
				throw new IllegalStateException("seq " + e.getKey() + " is taken by other content; pick a new id");
			}
			seqs.put(e.getKey(), copy);
			seqsWritten++;
		}
		if (seqsWritten > 0) {
			writeGroup(out, SEQUENCE_GROUP, seqs);
			out.close();
			out = new Store(new File(a[0]));
			out.load();
			System.out.println("RESULT silent seqs written: " + seqsWritten);
		}
		for (var e : RECOLOURED_COPIES.entrySet()) {
			int[] spec = e.getValue();
			byte[] source = files.get(spec[0]);
			if (source == null)
				throw new IllegalStateException("spotanim " + spec[0] + " missing from the cache");
			byte[] copy = recoloured(source, spec[1], java.util.Arrays.copyOfRange(spec, 2, spec.length),
					SCALES.getOrDefault(e.getKey(), -1));
			byte[] existing = files.get(e.getKey());
			if (existing != null) {
				if (Arrays.equals(existing, copy))
					continue;
				throw new IllegalStateException("spotanim " + e.getKey() + " is taken by other content; pick a new id");
			}
			files.put(e.getKey(), copy);
			written++;
		}
		for (var e : STATIC_COPIES.entrySet()) {
			byte[] source = files.get(e.getValue());
			if (source == null)
				throw new IllegalStateException("spotanim " + e.getValue() + " missing from the cache");
			byte[] copy = withoutSequence(source);
			byte[] existing = files.get(e.getKey());
			if (existing != null) {
				if (Arrays.equals(existing, copy))
					continue;
				throw new IllegalStateException("spotanim " + e.getKey() + " is taken by other content; pick a new id");
			}
			files.put(e.getKey(), copy);
			written++;
		}
		if (written == 0) {
			out.close();
			System.out.println("RESULT raid4 spotanims already present");
			return;
		}

		Index index = out.getIndex(net.runelite.cache.IndexType.CONFIGS);
		Archive archive = index.getArchive(SPOTANIM_GROUP);
		FileData[] fd = new FileData[files.size()];
		List<byte[]> contents = new ArrayList<>();
		int i = 0;
		for (var f : files.entrySet()) {
			fd[i] = new FileData();
			fd[i].setId(f.getKey());
			i++;
			contents.add(f.getValue());
		}
		byte[] raw = SpliceItemOption.joinChunks(contents);
		Container c = new Container(archive.getCompression(), -1);
		c.compress(raw, null);
		out.getStorage().store(index.getId(), archive.getArchiveId(), c.data);
		archive.setFileData(fd);
		archive.setCrc(c.crc);
		archive.setRevision(archive.getRevision() + 1);
		archive.setCompressedSize(c.data.length);
		archive.setDecompressedSize(raw.length);
		IndexData indexData = index.toIndexData();
		byte[] rawIndex = indexData.writeIndexData();
		Container ic = new Container(index.getCompression(), -1);
		ic.compress(rawIndex, null);
		out.getStorage().store(255, index.getId(), ic.data);
		index.setCrc(ic.crc);
		out.close();

		Store check = new Store(new File(a[0]));
		check.load();
		var onDisk = RoundTripDefs.files(check, SPOTANIM_GROUP);
		if (!onDisk.keySet().equals(files.keySet()))
			throw new IllegalStateException("spotanim ids differ after write");
		for (var f : files.entrySet()) {
			if (!Arrays.equals(onDisk.get(f.getKey()), f.getValue()))
				throw new IllegalStateException("spotanim " + f.getKey() + " differs after write");
		}
		check.close();
		System.out.println("RESULT raid4 spotanims written and verified: " + written + " added, " + files.size() + " total");
	}

	/** A def with one opcode's segment dropped. */
	static byte[] withoutOpcode(String type, byte[] def, int opcode) {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		for (byte[] seg : DefRewriter.segments(type, def, DefRewriter.IDENTITY, k -> null))
			if ((seg[0] & 0xff) != opcode)
				b.writeBytes(seg);
		b.write(0);
		return b.toByteArray();
	}

	/** A spotanim copy: the source's segments without its sequence / recolours, then its new sequence and recolours. */
	static byte[] recoloured(byte[] source, int seq, int[] pairs, int scale) {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		for (byte[] seg : DefRewriter.segments("spotanim", source, DefRewriter.IDENTITY, k -> null)) {
			int op = seg[0] & 0xff;
			if (op == 40 || (seq >= 0 && op == SEQUENCE_OPCODE) || (scale > 0 && (op == 4 || op == 5)))
				continue;
			b.writeBytes(seg);
		}
		if (scale > 0)
			for (int op : new int[] { 4, 5 }) {
				b.write(op);
				b.write(scale >> 8 & 0xff);
				b.write(scale & 0xff);
			}
		if (seq >= 0) {
			b.write(SEQUENCE_OPCODE);
			b.write(seq >> 8 & 0xff);
			b.write(seq & 0xff);
		}
		b.write(40);
		b.write(pairs.length / 2);
		for (int v : pairs) {
			b.write(v >> 8 & 0xff);
			b.write(v & 0xff);
		}
		b.write(0);
		return b.toByteArray();
	}

	/** Writes one CONFIGS group from {file id -> bytes} and the index. */
	static void writeGroup(Store out, int group, TreeMap<Integer, byte[]> files) throws Exception {
		Index index = out.getIndex(net.runelite.cache.IndexType.CONFIGS);
		Archive archive = index.getArchive(group);
		FileData[] fd = new FileData[files.size()];
		List<byte[]> contents = new ArrayList<>();
		int i = 0;
		for (var f : files.entrySet()) {
			fd[i] = new FileData();
			fd[i].setId(f.getKey());
			i++;
			contents.add(f.getValue());
		}
		byte[] raw = SpliceItemOption.joinChunks(contents);
		Container c = new Container(archive.getCompression(), -1);
		c.compress(raw, null);
		out.getStorage().store(index.getId(), archive.getArchiveId(), c.data);
		archive.setFileData(fd);
		archive.setCrc(c.crc);
		archive.setRevision(archive.getRevision() + 1);
		archive.setCompressedSize(c.data.length);
		archive.setDecompressedSize(raw.length);
		IndexData indexData = index.toIndexData();
		byte[] rawIndex = indexData.writeIndexData();
		Container ic = new Container(index.getCompression(), -1);
		ic.compress(rawIndex, null);
		out.getStorage().store(255, index.getId(), ic.data);
		index.setCrc(ic.crc);
	}

	/** The source's opcodes (its terminating 0 dropped), then opcode 40 with the pairs, then 0. */
	static byte[] withRecolours(byte[] source, int[] pairs) {
		if (source.length == 0 || source[source.length - 1] != 0)
			throw new IllegalStateException("spotanim does not end in opcode 0");
		java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
		b.write(source, 0, source.length - 1);
		b.write(40);
		b.write(pairs.length / 2);
		for (int v : pairs) {
			b.write(v >> 8 & 0xff);
			b.write(v & 0xff);
		}
		b.write(0);
		return b.toByteArray();
	}

	static byte[] withoutSequence(byte[] def) {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		for (byte[] seg : DefRewriter.segments("spotanim", def, DefRewriter.IDENTITY, k -> null)) {
			if ((seg[0] & 0xff) != SEQUENCE_OPCODE)
				b.writeBytes(seg);
		}
		b.write(0);
		return b.toByteArray();
	}
}
