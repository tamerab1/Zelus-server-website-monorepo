import io.ruin.api.utils.BCrypt;
public class VerifyOne {
    public static void main(String[] args) {
        System.out.println(BCrypt.checkpw(args[0], args[1]));
    }
}
