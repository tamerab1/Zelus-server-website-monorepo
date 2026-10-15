import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Store;
import net.runelite.cache.io.InputStream;

// Clones a sequence into a new id, patching its rightHandItem (opcode 7) in place -- every real
// pickaxe tier's swing animation has rightHandItem baked in pointing at THAT tier's own item
// (confirmed via CheckSeqHandItems: Rune=1787, Dragon=12432, Infernal=13755, Crystal=24192), which
// forces that model to render in-hand regardless of what's actually equipped. A brand new custom
// pickaxe needs its own dedicated sequence with its own id in this field to show correctly.
//
// Only opcodes 1-6 need to be understood (to walk far enough to find opcode 7) -- once opcode 7's
// 2-byte payload is located, it's overwritten in place and everything else in the record (whatever
// it is) is left byte-for-byte untouched, so nothing past opcode 7 needs to be parsed at all.
// Usage: verify|apply <cachePath> <sourceSeqId> <newSeqId> <rightHandItemId>
public class CloneSeqWithHandItem {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int sourceSeqId = Integer.parseInt(args[2]);
        int newSeqId = Integer.parseInt(args[3]);
        int rightHandItemId = Integer.parseInt(args[4]);

        try (Store store = new Store(new java.io.File(cachePath))) {
            store.load();
            boolean ok = PackMadAngel.verifyConfigPairs(store, ConfigType.SEQUENCE.getId(),
                    new int[][]{{sourceSeqId, newSeqId}});
            System.out.println(ok ? "VERIFY: new seq id free." : "VERIFY: FAILED, see above.");
            if (!ok) return;
            if (mode.equals("verify")) {
                System.out.println("(dry run -- pass 'apply' to write)");
                return;
            }
            if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");

            PackMadAngel.applyConfig(store.getStorage(), store.getIndex(IndexType.CONFIGS),
                    store.getStorage(), store.getIndex(IndexType.CONFIGS),
                    ConfigType.SEQUENCE.getId(), new int[][]{{sourceSeqId, newSeqId}},
                    (id, raw) -> setRightHandItem(raw, rightHandItemId));

            System.out.println("APPLY complete: seq " + newSeqId + " cloned from " + sourceSeqId
                    + " with rightHandItem=" + rightHandItemId);
        }
    }

    static byte[] setRightHandItem(byte[] raw, int newValue) {
        InputStream is = new InputStream(raw);
        while (true) {
            int opcode = is.readUnsignedByte();
            if (opcode == 7) {
                int valueOffset = is.getOffset();
                byte[] out = raw.clone();
                out[valueOffset] = (byte) ((newValue >> 8) & 0xFF);
                out[valueOffset + 1] = (byte) (newValue & 0xFF);
                return out;
            }
            if (opcode == 0) throw new IllegalStateException("no opcode 7 (rightHandItem) found in this sequence");
            skipKnown(is, opcode);
        }
    }

    // Only needs to correctly skip whatever comes BEFORE opcode 7 -- verified via ListSeqOpcodes
    // that opcodes 1-6 are what precede it in every real pickaxe swing sequence checked so far.
    static void skipKnown(InputStream is, int opcode) {
        if (opcode == 1) {
            int count = is.readUnsignedShort();
            for (int i = 0; i < count; i++) is.readUnsignedShort();
            for (int i = 0; i < count; i++) is.readUnsignedShort();
            for (int i = 0; i < count; i++) is.readUnsignedShort();
        } else if (opcode == 2) {
            is.readUnsignedShort();
        } else if (opcode == 3) {
            int count = is.readUnsignedByte();
            for (int i = 0; i < count; i++) is.readUnsignedByte();
        } else if (opcode == 4) {
            // no payload
        } else if (opcode == 5) {
            is.readUnsignedByte();
        } else if (opcode == 6) {
            is.readUnsignedShort();
        } else {
            throw new RuntimeException("unmapped opcode " + opcode + " before opcode 7 -- inspect manually with ListSeqOpcodes");
        }
    }
}
