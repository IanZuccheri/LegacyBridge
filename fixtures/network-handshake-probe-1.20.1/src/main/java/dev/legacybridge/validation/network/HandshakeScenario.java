package dev.legacybridge.validation.network;

/** Closed negative-peer inventory. No game references or inferred compatibility. */
enum HandshakeScenario {
    MATCHED, CLIENT_ABSENT, CLIENT_PROTOCOL, SERVER_ABSENT, SERVER_PROTOCOL, PLATFORM_CLIENT_ABSENT;

    static HandshakeScenario parse(String text) {
        if (text == null) throw new IllegalArgumentException("Explicit handshake scenario required");
        return valueOf(text);
    }
    boolean fixturePresent(String role) {
        checkRole(role);
        if (platformBaseline()) return false;
        if (this == MATCHED) return true;
        return role.equals("server") != name().startsWith("SERVER_");
    }
    boolean emulateProtocol(String role) { return name().endsWith("_PROTOCOL") && !fixturePresent(role); }
    String rejectingRole() { return this == MATCHED ? "NONE" : platformBaseline() || name().startsWith("CLIENT_") ? "server" : "a"; }
    boolean platformBaseline() { return this == PLATFORM_CLIENT_ABSENT; }
    void validateInventory(java.util.Set<String> modIds, String role) {
        checkRole(role);
        if (platformBaseline()) {
            if (!modIds.equals(java.util.Set.of("minecraft", "forge", "legacybridge_handshake_probe")))
                throw new IllegalStateException("Platform baseline requires only Minecraft, Forge and QA observer");
        } else if (!modIds.contains("legacybridge_runtime")
                || modIds.contains("legacybridge_network") != fixturePresent(role)) {
            throw new IllegalStateException("Normal case requires runtime and exact fixture inventory");
        }
    }
    static void checkRole(String role) {
        if (!"server".equals(role) && !"a".equals(role)) throw new IllegalArgumentException("Only server/a are handshake roles");
    }
}
