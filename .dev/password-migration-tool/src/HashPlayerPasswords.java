import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.ruin.api.utils.BCrypt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// One-time batch migration: hashes every plaintext password field in the player save
// tree with the exact same BCrypt.hashpw used by PlayerLoginWorker/Player.java at
// runtime, so login switches straight to BCrypt.checkpw with no plaintext fallback
// ever existing. Only touches the "password" key via generic JSONObject -- never
// binds to the Player class -- so every other field round-trips byte-for-byte
// untouched, regardless of Player's actual field set at the time this runs.
//
// Idempotent: a value already in bcrypt's "$2a$"/"$2b$"/"$2y$" format is left alone,
// so this is safe to run more than once (e.g. re-run after a final pre-cutover sync
// pulls in a few more plaintext saves from players still active on the old box).
//
// Usage: java -cp <classpath> HashPlayerPasswords <playerSavesRootDir> [--dry-run]
public class HashPlayerPasswords {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: HashPlayerPasswords <playerSavesRootDir> [--dry-run]");
            System.exit(1);
        }
        Path root = Paths.get(args[0]);
        boolean dryRun = args.length > 1 && args[1].equals("--dry-run");
        if (!Files.isDirectory(root)) {
            System.err.println("Not a directory: " + root);
            System.exit(1);
        }

        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(p -> p.toString().endsWith(".json")).collect(Collectors.toList());
        }

        int hashed = 0, alreadyHashed = 0, noPassword = 0, errors = 0;
        for (Path file : files) {
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                JSONObject obj = JSON.parseObject(content);
                Object rawPassword = obj.get("password");
                if (rawPassword == null) {
                    noPassword++;
                    continue;
                }
                String password = rawPassword.toString();
                if (password.isEmpty()) {
                    noPassword++;
                    continue;
                }
                if (password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$")) {
                    alreadyHashed++;
                    continue;
                }
                String newHash = BCrypt.hashpw(password, BCrypt.gensalt());
                obj.put("password", newHash);
                if (!dryRun) {
                    Files.writeString(file, JSON.toJSONString(obj), StandardCharsets.UTF_8);
                }
                hashed++;
            } catch (Exception e) {
                errors++;
                System.err.println("FAILED on " + file + ": " + e);
            }
        }

        System.out.println("Scanned " + files.size() + " files.");
        System.out.println("Hashed: " + hashed + (dryRun ? " (dry-run, not written)" : ""));
        System.out.println("Already hashed: " + alreadyHashed);
        System.out.println("No password field: " + noPassword);
        System.out.println("Errors: " + errors);
        if (errors > 0) {
            System.exit(2);
        }
    }
}
