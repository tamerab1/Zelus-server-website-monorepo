public class PreviewMany { public static void main(String[] a) throws Exception {
 // a[0]=cache, then triples dir group out ; shared opts after "--"
 int sep = java.util.Arrays.asList(a).indexOf("--"); String[] opts = java.util.Arrays.copyOfRange(a, sep + 1, a.length);
 IfPreview.loadOnce(a[0], opts);
 for (int i = 1; i + 2 < sep + 1; i += 3) IfPreview.render(a[i], Integer.parseInt(a[i + 1]), a[i + 2], opts); } }
