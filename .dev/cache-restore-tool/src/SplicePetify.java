import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.loaders.NpcLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;
import net.runelite.cache.io.InputStream;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// Creates a new NPC def as a copy of an existing one (new id, source untouched), with:
//   - name replaced
//   - widthScale/heightScale (opcode 97/98) replaced or inserted -- shrinks a boss model to pet size
//   - action slot 0 (opcode 30) set to "Talk-to", action slot 2 (opcode 32) set to "Pick-up"
//     (Pet.java's generic pickup/talk-to handlers match these exact strings case-insensitively --
//     without them, the NPCAction.register("pick-up"/"talk-to", ...) hooks never fire)
// Model/animations are left untouched -- same model+skeleton as the source boss, so existing
// walk/stand animations carry over correctly (only the visual scale changes).
//
// Modes:
//   verify <cachePath> <sourceNpcId> <newNpcId> <newName> <widthScale> <heightScale>
//   apply  <cachePath> <sourceNpcId> <newNpcId> <newName> <widthScale> <heightScale>
public class SplicePetify {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int sourceId = Integer.parseInt(args[2]);
        int newId = Integer.parseInt(args[3]);
        String newName = args[4];
        int widthScale = Integer.parseInt(args[5]);
        int heightScale = Integer.parseInt(args[6]);
        int size = args.length > 7 && !args[7].equals("-") ? Integer.parseInt(args[7]) : 1;
        String colorPairsArg = args.length > 8 && !args[8].equals("-") ? args[8] : null;
        // Optional extra model id to APPEND to the existing models[] list (opcode 1) -- e.g. a
        // hat model. EXPERIMENTAL: opcode-1 models render combined at the NPC's own local origin,
        // the same mechanism that already works for this cache's existing multi-part pets (body +
        // wing meshes authored together by the same artist). A worn-equipment model like a party
        // hat was authored for a DIFFERENT anchor system (a player's head bone), not this one, so
        // there's no guarantee it lands in a sane position -- verify in-game before trusting it.
        int extraModelId = args.length > 9 ? Integer.parseInt(args[9]) : -1;
        // Optional hover height (opcode 124, unsigned short) -- the real OSRS mechanism used to
        // render flying/floating creatures (bats, ghosts) elevated above their tile instead of
        // grounded. "-" or omitted leaves the source's own value (usually unset/-1) untouched.
        int hoverHeight = args.length > 10 && !args[10].equals("-") ? Integer.parseInt(args[10]) : -1;

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.NPC.getId());

            byte[] compressed = storage.loadArchive(archive);
            byte[] decompressed = archive.decompress(compressed);
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            for (FileData fd : fileData) {
                if (fd.getId() == newId) {
                    throw new IllegalStateException("id " + newId + " already exists -- refusing to overwrite.");
                }
            }

            int sourceSlot = -1;
            for (int i = 0; i < fileData.length; i++) {
                if (fileData[i].getId() == sourceId) {
                    sourceSlot = i;
                    break;
                }
            }
            if (sourceSlot == -1) {
                throw new IllegalStateException("source id " + sourceId + " not found");
            }

            byte[] work = fileContents.get(sourceSlot);

            // Some source pets (e.g. Lil' Xarp/Sot) already have a working Pick-up/Talk-to
            // somewhere in their actions array, just not at slot 0/2 specifically (Guardian
            // Mummy/Mimic genuinely lacked both anywhere). Forcing slot 0/2 unconditionally
            // would duplicate an already-working option and clobber whatever was really there
            // (e.g. Metamorphosis) -- only touch a slot if that exact action isn't present ANYWHERE
            // in the source's actions already.
            NpcLoader preLoader = new NpcLoader();
            NpcDefinition preCheck = preLoader.load(sourceId, work);
            boolean hasTalkTo = hasAction(preCheck.actions, "Talk-to");
            boolean hasPickUp = hasAction(preCheck.actions, "Pick-up");

            byte[] nameBytes = newName.getBytes(StandardCharsets.US_ASCII);
            byte[] namePayload = new byte[nameBytes.length + 1];
            System.arraycopy(nameBytes, 0, namePayload, 0, nameBytes.length);
            namePayload[namePayload.length - 1] = 0;
            work = replaceOrInsert(work, 2, namePayload);

            work = replaceOrInsert(work, 97, twoBytes(widthScale));
            work = replaceOrInsert(work, 98, twoBytes(heightScale));
            work = replaceOrInsert(work, 12, new byte[]{(byte) size});

            if (hoverHeight >= 0) {
                work = replaceOrInsert(work, 124, twoBytes(hoverHeight));
            }

            if (extraModelId > 0) {
                int[] span = findOpcodeSpan(work, 1);
                if (span == null) {
                    throw new IllegalStateException("no opcode-1 (models) found on source -- ABORTING");
                }
                // Decode existing model list, append the extra one, re-encode.
                InputStream ms = new InputStream(work);
                ms.setOffset(span[0] + 1);
                int count = ms.readUnsignedByte();
                int[] existing = new int[count];
                for (int i = 0; i < count; i++) existing[i] = ms.readUnsignedShort();

                java.io.ByteArrayOutputStream modelsBlock = new java.io.ByteArrayOutputStream();
                modelsBlock.write(count + 1);
                for (int m : existing) {
                    modelsBlock.write((m >> 8) & 0xFF);
                    modelsBlock.write(m & 0xFF);
                }
                modelsBlock.write((extraModelId >> 8) & 0xFF);
                modelsBlock.write(extraModelId & 0xFF);
                work = replaceOrInsert(work, 1, modelsBlock.toByteArray());
            }

            if (!hasTalkTo) {
                work = replaceOrInsert(work, 30, withTerminator("Talk-to"));
            }
            if (!hasPickUp) {
                work = replaceOrInsert(work, 32, withTerminator("Pick-up"));
            }

            if (colorPairsArg != null) {
                String[] pairsStr = colorPairsArg.split(",");
                short[] find = new short[pairsStr.length / 2];
                short[] replace = new short[pairsStr.length / 2];
                for (int i = 0; i < find.length; i++) {
                    find[i] = (short) Integer.parseInt(pairsStr[i * 2].trim());
                    replace[i] = (short) Integer.parseInt(pairsStr[i * 2 + 1].trim());
                }
                java.io.ByteArrayOutputStream colorBlock = new java.io.ByteArrayOutputStream();
                colorBlock.write(find.length);
                for (int i = 0; i < find.length; i++) {
                    colorBlock.write((find[i] >> 8) & 0xFF);
                    colorBlock.write(find[i] & 0xFF);
                    colorBlock.write((replace[i] >> 8) & 0xFF);
                    colorBlock.write(replace[i] & 0xFF);
                }
                work = replaceOrInsert(work, 40, colorBlock.toByteArray());
            }

            byte[] newRaw = work;

            NpcLoader loader = new NpcLoader();
            NpcDefinition before = loader.load(sourceId, fileContents.get(sourceSlot));
            NpcDefinition after = loader.load(newId, newRaw);
            System.out.println("BEFORE (source " + sourceId + "): name=\"" + before.name + "\" models=" + java.util.Arrays.toString(before.models)
                    + " size=" + before.size + " widthScale=" + before.widthScale + " heightScale=" + before.heightScale + " actions=" + java.util.Arrays.toString(before.actions));
            System.out.println("AFTER  (new " + newId + "):    name=\"" + after.name + "\" models=" + java.util.Arrays.toString(after.models)
                    + " size=" + after.size + " widthScale=" + after.widthScale + " heightScale=" + after.heightScale + " actions=" + java.util.Arrays.toString(after.actions)
                    + " standingAnimation=" + after.standingAnimation + " walkingAnimation=" + after.walkingAnimation
                    + " height=" + after.height);
            if (hoverHeight >= 0 && after.height != hoverHeight) {
                throw new IllegalStateException("height not applied correctly -- ABORTING");
            }
            if (colorPairsArg != null) {
                System.out.println("  recolorToFind=" + java.util.Arrays.toString(after.recolorToFind)
                        + " recolorToReplace=" + java.util.Arrays.toString(after.recolorToReplace));
                if (after.recolorToFind == null) {
                    throw new IllegalStateException("recolor not applied correctly -- ABORTING");
                }
            }

            if (extraModelId <= 0 && !java.util.Arrays.equals(before.models, after.models)) {
                throw new IllegalStateException("splice changed models unexpectedly -- ABORTING");
            }
            if (extraModelId > 0) {
                java.util.List<Integer> expected = new java.util.ArrayList<>();
                for (int m : before.models) expected.add(m);
                expected.add(extraModelId);
                java.util.List<Integer> actual = new java.util.ArrayList<>();
                for (int m : after.models) actual.add(m);
                if (!expected.equals(actual)) {
                    throw new IllegalStateException("extra model not appended correctly -- ABORTING (expected "
                            + expected + " got " + actual + ")");
                }
            }
            if (!newName.equals(after.name)) {
                throw new IllegalStateException("name not applied correctly -- ABORTING");
            }
            if (after.widthScale != widthScale || after.heightScale != heightScale) {
                throw new IllegalStateException("scale not applied correctly -- ABORTING");
            }
            if (after.size != size) {
                throw new IllegalStateException("size not applied correctly -- ABORTING");
            }
            if (!hasAction(after.actions, "Talk-to") || !hasAction(after.actions, "Pick-up")) {
                throw new IllegalStateException("actions not applied correctly -- ABORTING");
            }

            if (mode.equals("verify")) {
                System.out.println("VERIFY mode -- not writing. Re-run with 'apply' to persist.");
                return;
            }

            fileContents.add(newRaw);
            FileData[] newFileData = new FileData[fileData.length + 1];
            System.arraycopy(fileData, 0, newFileData, 0, fileData.length);
            FileData newEntry = new FileData();
            newEntry.setId(newId);
            newEntry.setNameHash(-1);
            newFileData[fileData.length] = newEntry;
            archive.setFileData(newFileData);

            byte[] newDecompressed = SpliceItemOption.joinChunks(fileContents);
            Container container = new Container(archive.getCompression(), -1);
            container.compress(newDecompressed, null);

            storage.store(index.getId(), archive.getArchiveId(), container.data);
            archive.setCrc(container.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(container.data.length);
            archive.setDecompressedSize(newDecompressed.length);

            IndexData indexData = index.toIndexData();
            byte[] rawIndex = indexData.writeIndexData();
            Container idxContainer = new Container(index.getCompression(), -1);
            idxContainer.compress(rawIndex, null);
            storage.store(255, index.getId(), idxContainer.data);
            index.setCrc(idxContainer.crc);

            System.out.println("APPLY complete. New npc id " + newId + " added. Archive revision now " + archive.getRevision() + ".");
        }
    }

    static boolean hasAction(String[] actions, String needle) {
        if (actions == null) return false;
        for (String a : actions) {
            if (needle.equalsIgnoreCase(a)) return true;
        }
        return false;
    }

    static byte[] twoBytes(int value) {
        return new byte[]{(byte) ((value >> 8) & 0xFF), (byte) (value & 0xFF)};
    }

    static byte[] withTerminator(String s) {
        byte[] b = s.getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[b.length + 1];
        System.arraycopy(b, 0, out, 0, b.length);
        out[out.length - 1] = 0;
        return out;
    }

    // Replaces the payload of the first occurrence of targetOpcode with newPayload (opcode byte
    // stays, payload is swapped -- length may differ, e.g. renaming). If targetOpcode isn't
    // present at all, inserts a new [opcode, newPayload] record right before the terminator.
    static byte[] replaceOrInsert(byte[] b, int targetOpcode, byte[] newPayload) {
        int[] span = findOpcodeSpan(b, targetOpcode);
        if (span != null) {
            int payloadStart = span[0] + 1; // span[0] is the opcode byte itself
            byte[] out = new byte[b.length - (span[1] - payloadStart) + newPayload.length];
            System.arraycopy(b, 0, out, 0, payloadStart);
            System.arraycopy(newPayload, 0, out, payloadStart, newPayload.length);
            System.arraycopy(b, span[1], out, payloadStart + newPayload.length, b.length - span[1]);
            return out;
        } else {
            int insertAt = findTerminatorOffset(b);
            byte[] out = new byte[b.length + 1 + newPayload.length];
            System.arraycopy(b, 0, out, 0, insertAt);
            out[insertAt] = (byte) targetOpcode;
            System.arraycopy(newPayload, 0, out, insertAt + 1, newPayload.length);
            System.arraycopy(b, insertAt, out, insertAt + 1 + newPayload.length, b.length - insertAt);
            return out;
        }
    }

    // Returns [opcodeByteOffset, endOfPayload) for the first occurrence of targetOpcode, or null.
    static int[] findOpcodeSpan(byte[] b, int targetOpcode) {
        InputStream is = new InputStream(b);
        while (true) {
            int start = is.getOffset();
            int opcode = is.readUnsignedByte();
            if (opcode == 0) {
                return null;
            }
            InsertNPCActions.skipOpcodePayload(opcode, is);
            if (opcode == targetOpcode) {
                return new int[]{start, is.getOffset()};
            }
        }
    }

    static int findTerminatorOffset(byte[] b) {
        InputStream is = new InputStream(b);
        while (true) {
            int start = is.getOffset();
            int opcode = is.readUnsignedByte();
            if (opcode == 0) {
                return start;
            }
            InsertNPCActions.skipOpcodePayload(opcode, is);
        }
    }
}
