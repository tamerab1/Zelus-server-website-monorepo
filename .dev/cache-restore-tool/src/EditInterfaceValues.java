import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

// Edits string / int values inside interface component records of one group by exact byte-pattern
// replacement, then re-decodes with the real InterfaceLoader and checks that ONLY the named field
// changed (and to the expected value) before anything is written.
// Spec lines:
//   str <child> <field> <old> -> <new>   e.g. str 169 actions * -> Upgrade   (op text; pattern = old+NUL, "[sp]" in old = a space)
//   int <child> <field> <old> <new>      e.g. int 2 onMouseOverListener 3579 832 (4-byte big-endian int)
//   med <child> <field> <old> <new>      e.g. med 8 clickMask 2 0 (3-byte big-endian, e.g. if3 clickMask)
//   hover <child> <script> <overArg> <leaveArg>
//        adds onMouseOver [script, event_com, overArg] + onMouseLeave [script, event_com, leaveArg]
//        (e.g. script 44 = if_setgraphic sprite swap). Only allowed when the component has no
//        listeners at all: its record then ends in 18 empty listener bytes + 3 empty trigger counts.
// Each pattern must occur exactly once in the component's bytes.
// Usage: verify|apply <cachePath> <group> <specFile>
public class EditInterfaceValues {
    static final Charset CP1252 = Charset.forName("windows-1252");

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        int group = Integer.parseInt(args[2]);
        List<String> spec = new ArrayList<>();
        for (String l : Files.readAllLines(new File(args[3]).toPath())) {
            l = l.trim();
            if (!l.isEmpty() && !l.startsWith("#")) spec.add(l);
        }

        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.INTERFACES);
            Archive archive = index.getArchive(group);
            ArchiveFiles files = archive.getFiles(storage.loadArchive(archive));
            InterfaceLoader loader = new InterfaceLoader();

            for (String line : spec) {
                String[] p = line.split("\\s+");
                int child = Integer.parseInt(p[1]);
                if (p[0].equals("hover")) {
                    addHover(files, loader, group, child, Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]));
                    continue;
                }
                String field = p[2];
                byte[] oldPat, newPat;
                if (p[0].equals("str")) {
                    String rest = line.substring(line.indexOf(field) + field.length()).trim();
                    String[] ab = rest.split("\\s*->\\s*", 2);
                    oldPat = withNul(ab[0].replace("[sp]", " "));
                    newPat = withNul(ab[1]);
                } else if (p[0].equals("int")) {
                    oldPat = intBytes(Integer.parseInt(p[3]));
                    newPat = intBytes(Integer.parseInt(p[4]));
                } else if (p[0].equals("med")) {
                    oldPat = medBytes(Integer.parseInt(p[3]));
                    newPat = medBytes(Integer.parseInt(p[4]));
                } else throw new IllegalArgumentException(line);

                FSFile file = files.findFile(child);
                if (file == null) throw new IllegalStateException("no component " + child);
                byte[] before = file.getContents();
                int at = indexOfUnique(before, oldPat, line);
                byte[] after = new byte[before.length - oldPat.length + newPat.length];
                System.arraycopy(before, 0, after, 0, at);
                System.arraycopy(newPat, 0, after, at, newPat.length);
                System.arraycopy(before, at + oldPat.length, after, at + newPat.length, before.length - at - oldPat.length);

                int widgetId = (group << 16) | child;
                InterfaceDefinition b = loader.load(widgetId, before);
                InterfaceDefinition a = loader.load(widgetId, after);
                for (Field f : InterfaceDefinition.class.getFields()) {
                    String bv = render(f.get(b)), av = render(f.get(a));
                    if (f.getName().equals(field)) {
                        if (bv.equals(av)) throw new IllegalStateException(line + ": " + field + " unchanged");
                        System.out.println(group + ":" + child + " " + field + ": " + bv + " -> " + av);
                    } else if (!bv.equals(av)) {
                        throw new IllegalStateException(line + ": unexpected change in " + f.getName() + " (" + bv + " -> " + av + ")");
                    }
                }
                file.setContents(after);
            }

            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            byte[] data = files.saveContents();
            Container container = new Container(archive.getCompression(), -1);
            container.compress(data, null);
            storage.store(index.getId(), archive.getArchiveId(), container.data);
            archive.setCrc(container.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(container.data.length);
            archive.setDecompressedSize(data.length);

            IndexData indexData = index.toIndexData();
            Container idxContainer = new Container(index.getCompression(), -1);
            idxContainer.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), idxContainer.data);
            index.setCrc(idxContainer.crc);
            System.out.println("APPLY complete -- group " + group + " revision " + archive.getRevision());
        }
    }

    static final int EVENT_COM = -2147483645;
    static final String[] LISTENERS = {"onLoadListener", "onMouseOverListener", "onMouseLeaveListener"};

    static void addHover(ArchiveFiles files, InterfaceLoader loader, int group, int child, int script, int over, int leave) throws Exception {
        FSFile file = files.findFile(child);
        if (file == null) throw new IllegalStateException("no component " + child);
        byte[] before = file.getContents();
        int tail = before.length - 21;
        for (int i = tail; i < before.length; i++)
            if (before[i] != 0) throw new IllegalStateException(child + ": listener block is not empty");
        int widgetId = (group << 16) | child;
        InterfaceDefinition b = loader.load(widgetId, before);
        if (b.hasListener) throw new IllegalStateException(child + ": already has listeners");

        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        o.write(before, 0, tail + 1);            // everything up to and including the empty onLoad byte
        writeListener(o, script, EVENT_COM, over);   // onMouseOver
        writeListener(o, script, EVENT_COM, leave);  // onMouseLeave
        o.write(before, tail + 3, before.length - tail - 3);
        byte[] after = o.toByteArray();

        InterfaceDefinition a = loader.load(widgetId, after);
        Object[] expOver = {script, EVENT_COM, over}, expLeave = {script, EVENT_COM, leave};
        if (!Arrays.deepEquals(a.onMouseOverListener, expOver) || !Arrays.deepEquals(a.onMouseLeaveListener, expLeave) || !a.hasListener)
            throw new IllegalStateException(child + ": hover listeners did not decode as expected");
        for (Field f : InterfaceDefinition.class.getFields()) {
            String n = f.getName();
            if (n.equals("onMouseOverListener") || n.equals("onMouseLeaveListener") || n.equals("hasListener")) continue;
            if (!render(f.get(b)).equals(render(f.get(a))))
                throw new IllegalStateException(child + ": unexpected change in " + n);
        }
        System.out.println(group + ":" + child + " hover: over=" + render(a.onMouseOverListener) + " leave=" + render(a.onMouseLeaveListener));
        file.setContents(after);
    }

    static void writeListener(java.io.ByteArrayOutputStream o, int... ints) throws Exception {
        o.write(ints.length);
        for (int v : ints) {
            o.write(0); // int argument
            o.write(intBytes(v));
        }
    }

    static byte[] withNul(String s) {
        byte[] b = s.getBytes(CP1252);
        return Arrays.copyOf(b, b.length + 1);
    }

    static byte[] intBytes(int v) {
        return new byte[]{(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v};
    }

    static byte[] medBytes(int v) {
        return new byte[]{(byte) (v >>> 16), (byte) (v >>> 8), (byte) v};
    }

    static int indexOfUnique(byte[] hay, byte[] needle, String what) {
        int found = -1;
        outer:
        for (int i = 0; i + needle.length <= hay.length; i++) {
            for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            if (found >= 0) throw new IllegalStateException(what + ": pattern occurs more than once");
            found = i;
        }
        if (found < 0) throw new IllegalStateException(what + ": pattern not found");
        return found;
    }

    static String render(Object v) {
        if (v == null) return "null";
        if (v instanceof Object[]) return Arrays.deepToString((Object[]) v);
        if (v instanceof int[]) return Arrays.toString((int[]) v);
        if (v instanceof boolean[]) return Arrays.toString((boolean[]) v);
        if (v instanceof byte[]) return Arrays.toString((byte[]) v);
        if (v instanceof short[]) return Arrays.toString((short[]) v);
        if (v instanceof long[]) return Arrays.toString((long[]) v);
        return v.toString();
    }
}
