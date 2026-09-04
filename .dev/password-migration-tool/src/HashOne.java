import io.ruin.api.utils.BCrypt;

public class HashOne {
    public static void main(String[] args) {
        System.out.println(BCrypt.hashpw(args[0], BCrypt.gensalt()));
    }
}
