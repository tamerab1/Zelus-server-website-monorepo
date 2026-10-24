import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.*;
import java.io.File;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;

/** Patch every local model's texture ids through the manifest; check with RuneLite's own loader. args: localCache manifest */
public class VerifyModelPatch {
	public static void main(String[] a) throws Exception {
		Store l = new Store(new File(a[0])); l.load();
		Manifest m = new Manifest(Path.of(a[1]));
		int unchanged = 0, patched = 0, bad = 0, err = 0, byteOnly = 0;
		for (Archive x : l.findIndex(7).getArchives()) {
			byte[] data = x.decompress(l.getStorage().loadArchive(x));
			byte[] out;
			try { out = ModelTexturePatcher.patch(data, t -> m.map("texture", t)); } catch (Exception e) { err++; continue; }
			if (Arrays.equals(out, data)) { unchanged++; continue; }
			// decoder-independent: only recorded texture positions may differ
			ModelTexturePatcher walk = new ModelTexturePatcher();
			walk.load(-1, data);
			Set<Integer> allowed = new HashSet<>();
			for (int[] t : walk.textureOffsets) { allowed.add(t[0]); allowed.add(t[0] + 1); }
			boolean ok = true;
			for (int i = 0; i < data.length; i++) if (data[i] != out[i] && !allowed.contains(i)) ok = false;
			ModelDefinition before, after;
			try {
				before = new ModelLoader().load(x.getArchiveId(), data);
				after = new ModelLoader().load(x.getArchiveId(), out);
			} catch (Exception e) {
				if (ok) { byteOnly++; } else bad++;
				continue;
			}
			for (int f = 0; f < before.faceCount; f++) {
				int bt = before.faceTextures == null ? -1 : before.faceTextures[f];
				int at = after.faceTextures == null ? -1 : after.faceTextures[f];
				if (at != (bt < 0 ? bt : m.map("texture", bt))) ok = false;
			}
			for (Field fl : ModelDefinition.class.getDeclaredFields()) {
				if (Modifier.isStatic(fl.getModifiers()) || fl.getName().equals("faceTextures") || fl.getName().endsWith("Normals")
					|| fl.getName().startsWith("faceTextureUCoordinates") || fl.getName().startsWith("faceTextureVCoordinates")) continue;
				fl.setAccessible(true);
				if (!Objects.deepEquals(fl.get(before), fl.get(after))) { ok = false; System.out.println("RESULT field " + fl.getName() + " changed in model " + x.getArchiveId()); }
			}
			if (ok) patched++; else bad++;
		}
		System.out.println("RESULT models unchanged=" + unchanged + " patched+verified(both checks)=" + patched + " patched+verified(byte check only, old loader cannot decode)=" + byteOnly + " BAD=" + bad + " patcherErrors=" + err);
	}
}
