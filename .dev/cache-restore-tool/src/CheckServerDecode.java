import io.ruin.api.filestore.FileStore;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

// Decodes item (group 10) or loc (group 6) records with the SERVER's own io.ruin.cache.ObjType / LocType decoder via
// reflection: an opcode the server doesn't know throws at boot, so new records are checked here first.
// Usage: CheckServerDecode <cachePath> obj|loc <id...>   (server classes + kronos-api on the classpath)
public class CheckServerDecode {
    public static void main(String[] args) throws Exception {
        FileStore store = new FileStore(args[0]);
        boolean obj = args[1].equals("obj");
        Class<?> type = Class.forName(obj ? "io.ruin.cache.ObjType" : "io.ruin.cache.LocType");
        Constructor<?> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        Method decode = null;
        for (Method m : type.getDeclaredMethods())
            if (m.getName().equals("decode") && m.getParameterCount() == 1) { decode = m; decode.setAccessible(true); }
        Class<?> inBuffer = Class.forName("io.ruin.api.buffer.InBuffer");
        Constructor<?> inCtor = inBuffer.getDeclaredConstructor(byte[].class);
        inCtor.setAccessible(true);
        for (int i = 2; i < args.length; i++) {
            int id = Integer.parseInt(args[i]);
            byte[] data = store.get(2).getFile(obj ? 10 : 6, id);
            if (data == null) { System.out.println(id + " -> NO DATA"); continue; }
            Object def = ctor.newInstance();
            Field idf = type.getDeclaredField("id");
            idf.setAccessible(true);
            idf.set(def, id);
            try {
                decode.invoke(def, inCtor.newInstance((Object) data));
            } catch (Exception e) {
                System.out.println(id + " -> DECODE THREW: " + e.getCause());
                continue;
            }
            System.out.println(id + " -> name=\"" + type.getField("name").get(def) + "\" ok");
        }
    }
}
