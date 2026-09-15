import io.ruin.api.filestore.FileStore;
import io.ruin.cache.ObjType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

// Decodes items using the LIVE SERVER's OWN io.ruin.cache.ObjType decoder (not RuneLite's
// ItemLoader) via reflection, since that's the class that actually determines wearPos2/wearPos3
// used in Appearance.send()'s avatar.setWornObj(slot, itemId, wearPos2, wearPos3) call -- the
// server-side path that can desync on any opcode ObjType.decode() doesn't know about.
public class CheckServerObjTypeDecode {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        FileStore store = new FileStore(cachePath);

        Constructor<ObjType> ctor = ObjType.class.getDeclaredConstructor();
        ctor.setAccessible(true);

        Method decodeMethod = null;
        for (Method m : ObjType.class.getDeclaredMethods()) {
            if (m.getName().equals("decode") && m.getParameterCount() == 1) {
                decodeMethod = m;
                decodeMethod.setAccessible(true);
                break;
            }
        }
        if (decodeMethod == null) throw new IllegalStateException("decode(InBuffer) method not found");

        Class<?> inBufferClass = Class.forName("io.ruin.api.buffer.InBuffer");
        Constructor<?> inBufferCtor = inBufferClass.getDeclaredConstructor(byte[].class);
        inBufferCtor.setAccessible(true);

        for (int i = 1; i < args.length; i++) {
            int id = Integer.parseInt(args[i]);
            byte[] data = store.get(2).getFile(10, id);
            if (data == null) {
                System.out.println(id + " -> NO DATA");
                continue;
            }
            ObjType def = ctor.newInstance();
            Field idField = ObjType.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(def, id);

            Object inBuffer = inBufferCtor.newInstance((Object) data);
            try {
                decodeMethod.invoke(def, inBuffer);
            } catch (Exception e) {
                System.out.println(id + " -> DECODE THREW: " + e.getCause());
                continue;
            }

            System.out.println(id + " -> name=\"" + def.name + "\""
                    + " inventoryModel=" + def.inventoryModel
                    + " placeholderMainId=" + def.placeholderMainId + " placeholderTemplateId=" + def.placeholderTemplateId
                    + " isPlaceholder=" + def.isPlaceholder() + " hasPlaceholder=" + def.hasPlaceholder());
        }
    }
}
