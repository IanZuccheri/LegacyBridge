package dev.legacybridge.validation.network;

import com.google.gson.JsonObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/** Reflection is limited to this authorized, already running QA fixture, never input analysis. */
final class FixtureAccess {
    private static Class<?> fixture;
    static String version() {
        String version = ModList.get().getModContainerById("legacybridge_network").orElseThrow().getModInfo().getVersion().toString();
        if (!version.equals("1.0.0")) throw new IllegalStateException("Unexpected fixture version");
        return version;
    }
    private static Object call(String name, Class<?>[] types, Object... values) {
        try {
            if (fixture == null) fixture = Class.forName("dev.legacybridge.fixture.network.NetworkSnapshot");
            Method method = fixture.getMethod(name, types);
            return method.invoke(null, values);
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException("Native fixture endpoint failed: " + name, failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Missing exact fixture endpoint: " + name, failure);
        }
    }
    static void send(ServerPlayer player, int sequence, int value) {
        call("sendTo", new Class<?>[]{ServerPlayer.class, int.class, int.class}, player, sequence, value);
    }
    static int read(String name) { return (Integer) call(name, new Class<?>[0]); }
    static long appliedThread() { return (Long) call("lastThreadId", new Class<?>[0]); }
    static void snapshot(JsonObject result) {
        for (String name : new String[]{"sequence", "value", "accepted", "rejected"}) result.addProperty(name, read(name));
        result.addProperty("lastThreadId", appliedThread());
    }
    static boolean expected(QaCommand command) {
        for (String name : new String[]{"sequence", "value", "accepted", "rejected"}) {
            if (read(name) != command.number(name)) return false;
        }
        return true;
    }
}
