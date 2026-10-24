import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.*;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;

/** For every CUSTOM model the rev 241 decoder rejects: add the trailer, then check it parses and the old loader decodes it identically. args: local v231 */
public class VerifyTrailerFix {
	public static void main(String[] a) throws Exception {
		Store l = new Store(new File(a[0])); l.load();
		Store b = new Store(new File(a[1])); b.load();
		Set<Integer> v231 = new HashSet<>();
		for (Archive x : b.findIndex(7).getArchives()) v231.add(x.getArchiveId());
		int fixed = 0, identical = 0, oldLoaderCannot = 0, failed = 0;
		for (Archive x : l.findIndex(7).getArchives()) {
			if (v231.contains(x.getArchiveId())) continue;
			byte[] d = x.decompress(l.getStorage().loadArchive(x));
			byte[] f;
			try { f = ModelTexturePatcher.addMissingTrailer(d); } catch (Exception e) { failed++; System.out.println("RESULT cannot fix " + x.getArchiveId() + ": " + e.getMessage()); continue; }
			if (f == d) continue;
			fixed++;
			try {
				ModelDefinition p = new ModelLoader().load(1, d), q = new ModelLoader().load(1, f);
				boolean same = true;
				for (Field fl : ModelDefinition.class.getDeclaredFields()) {
					if (Modifier.isStatic(fl.getModifiers()) || fl.getName().endsWith("Normals")) continue;
					fl.setAccessible(true);
					if (!Objects.deepEquals(fl.get(p), fl.get(q))) { same = false; System.out.println("RESULT " + x.getArchiveId() + " field " + fl.getName() + " differs"); }
				}
				if (same) identical++;
			} catch (Exception e) { oldLoaderCannot++; }
		}
		System.out.println("RESULT custom models given a trailer=" + fixed + " (old-loader decode identical=" + identical + ", old loader cannot decode=" + oldLoaderCannot + ") unfixable=" + failed);
	}
}
