import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.loaders.NpcLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

// Custom bosses 16200-16214 (2026-10-19, "new customs/bosses"). Each npc is written from scratch -- one ripped model,
// name, size, stand / walk, "Attack", combat level, scale -- so no template recolours or rig data come along
// (see reference_npc_clone_strip_template_animations). The stand / walk ids were picked with FindBossRig: humanoid
// rips are skinned on the player skeleton (0), the creatures on the npc skeleton that tears their faces least.
// Spec lines:
//   boss <npcId> <rawModel> <modelId> <size> <scale> <combatLevel> <stand> <walk> <name...>     (-1 = no animation)
// Modes:
//   models <cache> <spec> <rawDir> <outDir>   <modelId>.dat per boss for InsertRawModels: the raw bytes as they are,
//                                             or re-encoded with faces whose texture is missing from the cache untextured
//                                             (they fall back to their own colour)
//   npcs verify|apply <cache> <spec>          sorted insert into the npc config archive, re-decoded and checked
//   npcs replace <cache> <spec>               rewrites the records of npcs that already exist (same slots)
public class BuildCustomBosses {
    record Boss(int npc, String raw, int model, int size, int scale, int level, int stand, int walk, String name) {}

    public static void main(String[] args) throws Exception {
        if (args[0].equals("models")) {
            models(args[1], read(args[2]), new File(args[3]), new File(args[4]));
        } else if (args[0].equals("npcs")) {
            npcs(args[1].equals("apply") || args[1].equals("replace"), args[1].equals("replace"), args[2], read(args[3]));
        } else {
            throw new IllegalArgumentException("mode: models | npcs");
        }
    }

    static List<Boss> read(String spec) throws Exception {
        List<Boss> out = new ArrayList<>();
        for (String line : Files.readAllLines(new File(spec).toPath())) {
            line = line.trim();
            if (!line.startsWith("boss ")) continue;
            String[] p = line.split("\\s+", 10);
            out.add(new Boss(Integer.parseInt(p[1]), p[2], Integer.parseInt(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]),
                    Integer.parseInt(p[6]), Integer.parseInt(p[7]), Integer.parseInt(p[8]), p[9]));
        }
        return out;
    }

    static void models(String cache, List<Boss> bosses, File rawDir, File outDir) throws Exception {
        outDir.mkdirs();
        Set<Integer> textures = new HashSet<>();
        try (Store store = new Store(new File(cache))) {
            store.load();
            for (Archive a : store.getIndex(IndexType.TEXTURES).getArchives())
                for (FileData fd : a.getFileData()) textures.add(fd.getId());
        }
        for (Boss b : bosses) {
            byte[] raw = Files.readAllBytes(new File(rawDir, b.raw() + ".dat").toPath());
            ModelDefinition m = new ModelLoader().load(b.model(), raw);
            int stripped = 0;
            if (m.faceTextures != null)
                for (int f = 0; f < m.faceCount; f++)
                    if (m.faceTextures[f] != -1 && !textures.contains((int) m.faceTextures[f])) {
                        m.faceTextures[f] = -1;
                        stripped++;
                    }
            byte[] out = raw;
            if (stripped > 0) {
                // the encoder cannot re-pack a partial texture table: drop the model's texturing entirely (the few faces
                // on textures the cache does have fall back to their colour too)
                m.faceTextures = null;
                m.textureCoords = null;
                m.numTextureFaces = 0;
                m.texIndices1 = m.texIndices2 = m.texIndices3 = null;
                m.textureRenderTypes = null;
                m.texturePrimaryColors = null;
                if (m.faceRenderTypes != null)
                    for (int f = 0; f < m.faceCount; f++) m.faceRenderTypes[f] &= 1;
                if (m.packedVertexGroups == null && m.getVertexGroups() != null)
                    m.packedVertexGroups = TextureModelFaces.packVertexGroups(m.getVertexGroups(), m.vertexCount);
                out = ModelType3Encoder.encode(m);
                ModelDefinition back = new ModelLoader().load(b.model(), out);
                if (back.vertexCount != m.vertexCount || back.faceCount != m.faceCount || !Arrays.equals(back.faceColors, m.faceColors)
                        || !Arrays.deepEquals(back.getVertexGroups(), new ModelLoader().load(b.model(), raw).getVertexGroups()))
                    throw new IllegalStateException(b.raw() + ": re-encode check failed");
            }
            Files.write(new File(outDir, b.model() + ".dat").toPath(), out);
            System.out.printf("%s -> model %d (%d faces%s)%n", b.raw(), b.model(), m.faceCount,
                    stripped > 0 ? ", " + stripped + " faces had missing textures: texturing dropped" : "");
        }
    }

    static byte[] record(Boss b) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(1);
        o.write(1);
        u16(o, b.model());
        o.write(2);
        str(o, b.name());
        o.write(12);
        o.write(b.size());
        if (b.stand() >= 0) { o.write(13); u16(o, b.stand()); }
        if (b.walk() >= 0) { o.write(14); u16(o, b.walk()); }
        o.write(31);
        str(o, "Attack");
        o.write(95);
        u16(o, b.level());
        if (b.scale() != 128) {
            o.write(97); u16(o, b.scale());
            o.write(98); u16(o, b.scale());
        }
        o.write(0);
        return o.toByteArray();
    }

    static void u16(ByteArrayOutputStream o, int v) {
        if (v < 0 || v > 0xffff) throw new IllegalArgumentException("not u16: " + v);
        o.write(v >> 8);
        o.write(v);
    }

    static void str(ByteArrayOutputStream o, String s) {
        o.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
        o.write(0);
    }

    static void npcs(boolean apply, boolean replace, String cache, List<Boss> bosses) throws Exception {
        try (Store store = new Store(new File(cache))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Index models = store.getIndex(IndexType.MODELS);
            Archive archive = index.getArchive(ConfigType.NPC.getId());
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(archive.decompress(storage.loadArchive(archive)), fileData.length);
            List<FileData> fds = new ArrayList<>(Arrays.asList(fileData));
            for (Boss b : bosses) {
                int existing = -1;
                for (int i = 0; i < fds.size(); i++)
                    if (fds.get(i).getId() == b.npc()) existing = i;
                if (existing >= 0 && !replace) throw new IllegalStateException("npc " + b.npc() + " already exists");
                if (existing < 0 && replace) throw new IllegalStateException("npc " + b.npc() + " does not exist to replace");
                if (apply && models.getArchive(b.model()) == null)
                    throw new IllegalStateException("model " + b.model() + " is not in the cache yet");
                byte[] rec = record(b);
                NpcDefinition d = new NpcLoader().load(b.npc(), rec);
                if (!d.name.equals(b.name()) || d.size != b.size() || d.models[0] != b.model() || d.standingAnimation != b.stand()
                        || d.walkingAnimation != b.walk() || d.combatLevel != b.level() || !"Attack".equals(d.actions[1])
                        || d.widthScale != b.scale() || d.heightScale != b.scale())
                    throw new IllegalStateException("npc " + b.npc() + ": record does not decode back");
                int at;
                if (replace) {
                    at = existing;
                    contents.set(at, rec);
                } else {
                    at = fds.size();
                    for (int i = 0; i < fds.size(); i++)
                        if (fds.get(i).getId() > b.npc()) { at = i; break; }
                    FileData fd = new FileData();
                    fd.setId(b.npc());
                    fd.setNameHash(-1);
                    fds.add(at, fd);
                    contents.add(at, rec);
                }
                System.out.printf("npc %d %-30s model %d size %d scale %d lvl %d stand %d walk %d (slot %d)%n", b.npc(), b.name(),
                        b.model(), b.size(), b.scale(), b.level(), b.stand(), b.walk(), at);
            }
            if (!apply) {
                System.out.println("VERIFY ok, nothing written");
                return;
            }
            archive.setFileData(fds.toArray(new FileData[0]));
            byte[] data = SpliceItemOption.joinChunks(contents);
            Container container = new Container(archive.getCompression(), -1);
            container.compress(data, null);
            storage.store(index.getId(), archive.getArchiveId(), container.data);
            archive.setCrc(container.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(container.data.length);
            archive.setDecompressedSize(data.length);
            IndexData indexData = index.toIndexData();
            Container idx = new Container(index.getCompression(), -1);
            idx.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), idx.data);
            index.setCrc(idx.crc);
            System.out.println("APPLY ok: " + bosses.size() + " npcs written");
        }
    }
}
