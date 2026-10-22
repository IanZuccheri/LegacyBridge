package dev.legacybridge.corpus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Inert child JVM used only by collector tests: no Minecraft, Forge, launcher credentials or network. */
public final class CompositionCollectorHelper {
    private CompositionCollectorHelper() { }
    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "ok" -> { System.out.print("OUT_OK\n"); System.err.print("ERR_OK\n"); }
            case "stdin" -> System.out.print(System.in.read() == -1 ? "STDIN_EOF\n" : "STDIN_NOT_CLOSED\n");
            case "exit" -> System.exit(7); // Only this inert test executable models a non-zero child exit.
            case "sleep" -> Thread.sleep(15_000);
            case "spam" -> { byte[] chunk = new byte[8192]; for (int i = 0; i < 2048; i++) System.out.write(chunk); }
            case "both" -> {
                Thread stderr = new Thread(() -> { for (int i = 0; i < 10000; i++) System.err.println("stderr"); }); stderr.start();
                for (int i = 0; i < 10000; i++) System.out.println("stdout"); stderr.join();
            }
            case "invalid" -> System.out.write(new byte[]{(byte) 0xc3, 0x28});
            case "sensitive" -> System.err.print("accessToken=synthetic-private-not-a-real-token\n");
            case "drift" -> { Files.writeString(Path.of("input.dat"), "changed"); System.out.println("DRIFT_WRITTEN"); }
            case "environment" -> {
                Set<String> allowed = Set.of("SYSTEMROOT", "WINDIR", "TEMP", "TMP", "HOME", "USERPROFILE", "GRADLE_USER_HOME");
                boolean clean = System.getenv().keySet().stream().allMatch(name -> allowed.contains(name.toUpperCase(java.util.Locale.ROOT)));
                boolean homes = Path.of(System.getProperty("user.home")).equals(Path.of("").toAbsolutePath().resolve("empty-user-home"))
                        && Path.of(System.getProperty("java.io.tmpdir")).equals(Path.of("").toAbsolutePath().resolve("tmp"));
                System.out.println("MINIMAL_ENVIRONMENT=" + clean + ";OWNED_HOMES=" + homes);
            }
            default -> throw new IllegalArgumentException("unknown synthetic mode");
        }
    }
}
