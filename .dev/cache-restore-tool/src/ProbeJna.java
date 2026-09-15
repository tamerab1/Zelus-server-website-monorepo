public class ProbeJna {
    public static void main(String[] args) throws Exception {
        Class<?> c = Class.forName("com.sun.jna.Pointer");
        System.out.println("loaded: " + c);
    }
}
