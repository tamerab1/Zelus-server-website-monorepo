import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.definitions.loaders.SpriteLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.fs.Container;

import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Installs the Division Rivals package (Desktop/Rivals-Interface-Final, 2026-10-15) with this project's own cache code,
// treating the package's prebuilt payloads purely as data (its Python installer is not run):
//   - interface group 3/5120 (103 IF3 components) and sprites 8/24200..24230 (one frame each);
//   - every payload's SHA-256 must match cache/patch-manifest.json ("patched_sha256");
//   - each container is decompressed, every component decoded with InterfaceLoader and every sprite with SpriteLoader;
//   - a target id must be free, or (re-run) hold exactly the same decompressed content;
//   - written like every other archive this toolbox writes (BuildSpinnerInterface.write / fresh container, no keys).
// Usage: verify|apply <cachePath> <packageDir>
public class InstallRivalsPayloads {
    static final int GROUP = 5120, COMPONENTS = 103, FIRST_SPRITE = 24200, LAST_SPRITE = 24230;

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        File pkg = new File(args[2]);
        String manifest = new String(Files.readAllBytes(new File(pkg, "cache/patch-manifest.json").toPath()), "UTF-8");
        Map<String, String> sha = new HashMap<>();
        Matcher m = Pattern.compile("\"patched_sha256\"\\s*:\\s*\"([0-9a-f]{64})\"[^}]*?\"payload\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL).matcher(manifest);
        while (m.find())
            sha.put(m.group(2), m.group(1));
        check(sha.size() == 1 + (LAST_SPRITE - FIRST_SPRITE + 1), "manifest lists " + sha.size() + " payloads, expected 32");

        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index interfaces = store.getIndex(IndexType.INTERFACES), sprites = store.getIndex(IndexType.SPRITES);

            // ---- interface 5120 ----
            byte[] ifData = payload(pkg, "payload/index-3/group-" + GROUP + ".bin", sha);
            ArchiveFiles files = new ArchiveFiles();
            for (int c = 0; c < COMPONENTS; c++)
                files.addFile(new FSFile(c));
            files.loadContents(ifData);
            List<byte[]> comps = new ArrayList<>();
            InterfaceLoader loader = new InterfaceLoader();
            for (int c = 0; c < COMPONENTS; c++) {
                byte[] raw = files.findFile(c).getContents();
                InterfaceDefinition d = loader.load((GROUP << 16) | c, raw);
                check(d != null, "component " + c + " does not decode");
                comps.add(raw);
            }
            Archive existingIf = interfaces.getArchive(GROUP);
            boolean ifSame = false;
            if (existingIf != null) {
                byte[] cur = existingIf.decompress(storage.loadArchive(existingIf));
                ifSame = Arrays.equals(cur, ifData);
                check(ifSame, "interface " + GROUP + " already exists with OTHER content -- refusing to overwrite");
            }
            System.out.println("interface " + GROUP + ": " + COMPONENTS + " components decode OK" + (ifSame ? " (already installed, identical)" : ""));

            // ---- sprites ----
            Map<Integer, byte[]> spriteData = new LinkedHashMap<>();
            int spriteCompression = sprites.getArchive(1043).getCompression();
            for (int id = FIRST_SPRITE; id <= LAST_SPRITE; id++) {
                byte[] data = payload(pkg, "payload/index-8/group-" + id + ".bin", sha);
                SpriteDefinition[] frames = new SpriteLoader().load(id, data);
                check(frames.length == 1 && frames[0].getWidth() > 0 && frames[0].getHeight() > 0, "sprite " + id + " does not decode to one frame");
                Archive existing = sprites.getArchive(id);
                if (existing != null) {
                    check(Arrays.equals(existing.decompress(storage.loadArchive(existing)), data),
                            "sprite " + id + " already exists with OTHER content -- refusing to overwrite");
                    System.out.println("sprite " + id + " already installed (identical)");
                    continue;
                }
                spriteData.put(id, data);
                System.out.println("sprite " + id + ": " + frames[0].getWidth() + "x" + frames[0].getHeight() + " OK");
            }

            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            if (!ifSame)
                BuildSpinnerInterface.write(storage, interfaces, interfaces.getArchive(1002), comps, GROUP);
            for (Map.Entry<Integer, byte[]> e : spriteData.entrySet())
                BuildChampionshipBoard.storeArchive(storage, sprites, e.getKey(), spriteCompression, e.getValue());
            if (!spriteData.isEmpty())
                BuildChampionshipBoard.storeIndex(storage, sprites);
            System.out.println("APPLY complete -- interface " + GROUP + (ifSame ? " (unchanged)" : "") + ", " + spriteData.size() + " sprites.");
        }
    }

    /** Reads a payload, checks its SHA-256 against the manifest and returns the decompressed group data. */
    static byte[] payload(File pkg, String rel, Map<String, String> sha) throws Exception {
        byte[] raw = Files.readAllBytes(new File(pkg, "cache/" + rel).toPath());
        String want = sha.get(rel);
        check(want != null, rel + " is not in the manifest");
        StringBuilder got = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(raw))
            got.append(String.format("%02x", b));
        check(got.toString().equals(want), rel + " SHA-256 mismatch");
        return Container.decompress(raw, null).data;
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("CHECK FAILED: " + what + " -- nothing written");
    }
}
