import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.loaders.NpcLoader;
import net.runelite.cache.definitions.loaders.ObjectLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;
import net.runelite.cache.io.InputStream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

// Renames an existing NPC or object def in place and overwrites / clears its option slots (opcodes 30-34), unlike
// InsertNPCActions / InsertObjectActions which refuse to overwrite. Every other field must decode identical before
// anything is written. Uses those tools' opcode parsers and chunk split/join (both archives have duplicate file ids).
// Usage: verify|apply <cachePath> npc|object <id> [name=<Name>] [<slot>=<Label>|<slot>=-] ...   (- clears the slot)
public class EditConfigNameActions {
    static final Charset CP1252 = Charset.forName("windows-1252");

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        boolean npc = args[2].equals("npc");
        int id = Integer.parseInt(args[3]);
        String name = null;
        String[] labels = new String[5];
        boolean[] touched = new boolean[5];
        for (int a = 4; a < args.length; a++) {
            String[] kv = args[a].split("=", 2);
            if (kv[0].equals("name")) {
                name = kv[1];
            } else {
                int slot = Integer.parseInt(kv[0]);
                touched[slot] = true;
                labels[slot] = kv[1].equals("-") ? null : kv[1];
            }
        }
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive((npc ? ConfigType.NPC : ConfigType.OBJECT).getId());
            byte[] data = archive.decompress(storage.loadArchive(archive));
            FileData[] fds = archive.getFileData();
            List<byte[]> files = SpliceItemOption.splitChunks(data, fds.length);
            int edited = 0;
            for (int i = 0; i < fds.length; i++) {
                if (fds[i].getId() != id)
                    continue;
                byte[] work = files.get(i);
                Object before = decode(npc, id, work);
                if (name != null)
                    work = replace(npc, work, 2, string(name));
                for (int s = 0; s < 5; s++) {
                    if (!touched[s])
                        continue;
                    work = labels[s] == null ? remove(npc, work, 30 + s) : replace(npc, work, 30 + s, string(labels[s]));
                }
                Object after = decode(npc, id, work);
                System.out.println("file slot " + i + " BEFORE " + describe(before));
                System.out.println("file slot " + i + " AFTER  " + describe(after));
                check(npc, before, after, name, touched, labels);
                files.set(i, work);
                edited++;
            }
            if (edited == 0)
                throw new IllegalStateException((npc ? "npc " : "object ") + id + " not found");
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            byte[] joined = SpliceItemOption.joinChunks(files);
            Container container = new Container(archive.getCompression(), -1);
            container.compress(joined, null);
            storage.store(index.getId(), archive.getArchiveId(), container.data);
            archive.setCrc(container.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(container.data.length);
            archive.setDecompressedSize(joined.length);
            IndexData indexData = index.toIndexData();
            Container ic = new Container(index.getCompression(), -1);
            ic.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), ic.data);
            index.setCrc(ic.crc);
            System.out.println("APPLY complete -- " + edited + " file slot(s) of " + (npc ? "npc " : "object ") + id + ".");
        }
    }

    static Object decode(boolean npc, int id, byte[] b) {
        return npc ? new NpcLoader().load(id, b) : new ObjectLoader().load(id, b);
    }

    static String describe(Object d) {
        if (d instanceof NpcDefinition n)
            return "name=\"" + n.name + "\" actions=" + Arrays.toString(n.actions);
        ObjectDefinition o = (ObjectDefinition) d;
        return "name=\"" + o.getName() + "\" actions=" + Arrays.toString(o.getActions());
    }

    /** Name/actions as asked; models, size, animations and colours unchanged. */
    static void check(boolean npc, Object before, Object after, String name, boolean[] touched, String[] labels) {
        String[] actionsBefore, actionsAfter;
        if (npc) {
            NpcDefinition b = (NpcDefinition) before, a = (NpcDefinition) after;
            if (!Arrays.equals(b.models, a.models) || b.size != a.size || b.standingAnimation != a.standingAnimation
                || !Arrays.equals(b.recolorToFind, a.recolorToFind) || b.combatLevel != a.combatLevel)
                throw new IllegalStateException("other npc fields changed -- ABORTING");
            if (name != null && !name.equals(a.name))
                throw new IllegalStateException("name not applied -- ABORTING");
            actionsBefore = b.actions;
            actionsAfter = a.actions;
        } else {
            ObjectDefinition b = (ObjectDefinition) before, a = (ObjectDefinition) after;
            if (!Arrays.equals(b.getObjectModels(), a.getObjectModels()) || b.getSizeX() != a.getSizeX() || b.getSizeY() != a.getSizeY()
                || b.getInteractType() != a.getInteractType())
                throw new IllegalStateException("other object fields changed -- ABORTING");
            if (name != null && !name.equals(a.getName()))
                throw new IllegalStateException("name not applied -- ABORTING");
            actionsBefore = b.getActions();
            actionsAfter = a.getActions();
        }
        for (int s = 0; s < 5; s++) {
            String expect = touched[s] ? labels[s] : actionsBefore[s];
            if (!java.util.Objects.equals(expect, actionsAfter[s]))
                throw new IllegalStateException("action " + s + " is \"" + actionsAfter[s] + "\", expected \"" + expect + "\" -- ABORTING");
        }
    }

    static byte[] string(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(s.getBytes(CP1252));
        out.write(0);
        return out.toByteArray();
    }

    static int[] span(boolean npc, byte[] b, int target) {
        InputStream is = new InputStream(b);
        while (true) {
            int start = is.getOffset();
            int opcode = is.readUnsignedByte();
            if (opcode == 0)
                return null;
            if (npc)
                InsertNPCActions.skipOpcodePayload(opcode, is);
            else
                InsertObjectActions.skipOpcodePayload(opcode, is);
            if (opcode == target)
                return new int[]{start, is.getOffset()};
        }
    }

    static byte[] replace(boolean npc, byte[] b, int opcode, byte[] payload) {
        int[] sp = span(npc, b, opcode);
        if (sp == null) { // insert before the terminator
            int at = b.length - 1;
            byte[] out = new byte[b.length + 1 + payload.length];
            System.arraycopy(b, 0, out, 0, at);
            out[at] = (byte) opcode;
            System.arraycopy(payload, 0, out, at + 1, payload.length);
            out[out.length - 1] = 0;
            return out;
        }
        int ps = sp[0] + 1;
        byte[] out = new byte[b.length - (sp[1] - ps) + payload.length];
        System.arraycopy(b, 0, out, 0, ps);
        System.arraycopy(payload, 0, out, ps, payload.length);
        System.arraycopy(b, sp[1], out, ps + payload.length, b.length - sp[1]);
        return out;
    }

    static byte[] remove(boolean npc, byte[] b, int opcode) {
        int[] sp = span(npc, b, opcode);
        if (sp == null)
            return b;
        byte[] out = new byte[b.length - (sp[1] - sp[0])];
        System.arraycopy(b, 0, out, 0, sp[0]);
        System.arraycopy(b, sp[1], out, sp[0], b.length - sp[1]);
        return out;
    }
}
