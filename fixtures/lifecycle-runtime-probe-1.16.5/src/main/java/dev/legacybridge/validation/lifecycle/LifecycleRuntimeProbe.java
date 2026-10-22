package dev.legacybridge.validation.lifecycle;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import net.minecraft.command.CommandSource;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Explicitly authorized runtime-only observations; never called by the static converter. */
@Mod(LifecycleRuntimeProbe.MOD_ID)
public final class LifecycleRuntimeProbe {
    public static final String MOD_ID = "legacybridge_lifecycle_probe";
    private static final String FIXTURE_ID = "legacybridge_lifecycle";
    private static final String PACKAGE = "dev.legacybridge.fixture.lifecycle.";
    private static final String ENTRY_CLASS = PACKAGE + "LifecycleContent";
    private static final String ITEM_CLASS = PACKAGE + "LifecycleItem";
    private static final String CONFIG_CLASS = PACKAGE + "LifecycleConfig";
    private static final String COMMAND_CLASS = PACKAGE + "LifecycleCommands";
    private static final String BASELINE = "1.16.5-forge-36.2.42";

    public LifecycleRuntimeProbe() {
        MinecraftForge.EVENT_BUS.addListener(this::serverStarted);
    }

    private void serverStarted(FMLServerStartedEvent event) {
        Throwable failure = null;
        try {
            String trace = captureBehavior(event.getServer());
            BehaviorTraceWriter.writeNew(Paths.get("lifecycle-trace.json"), trace);
            System.out.println("LEGACYBRIDGE_LIFECYCLE_BEHAVIOR_OK baseline=" + BASELINE
                    + " fixture=legacybridge_lifecycle version=1.0.0 trace=lifecycle-trace.json");
        } catch (Throwable observed) {
            failure = observed;
            System.err.println("LEGACYBRIDGE_LIFECYCLE_BEHAVIOR_FAILED baseline=" + BASELINE);
            observed.printStackTrace(System.err);
        } finally {
            try {
                event.getServer().execute(() -> event.getServer().halt(false));
            } catch (Throwable shutdownFailure) {
                System.err.println("LEGACYBRIDGE_LIFECYCLE_BEHAVIOR_FAILED shutdown baseline=" + BASELINE);
                shutdownFailure.printStackTrace(System.err);
                if (failure != null) shutdownFailure.addSuppressed(failure);
                throw new IllegalStateException("Cannot schedule orderly probe shutdown", shutdownFailure);
            }
        }
        if (failure != null) throw new IllegalStateException("Lifecycle runtime comparison probe failed", failure);
    }

    private static String captureBehavior(MinecraftServer server) throws Exception {
        boolean serverThread = server.isSameThread();
        require(serverThread, "Runtime probe must run on the native server thread");
        ProbeExpectations expected = ProbeExpectations.read();
        require(ModList.get().getModContainerById(FIXTURE_ID).isPresent(), "Fixture mod not loaded");
        String fixtureVersion = ModList.get().getModContainerById(FIXTURE_ID).get()
                .getModInfo().getVersion().toString();
        require("1.0.0".equals(fixtureVersion), "Unexpected fixture version");
        Object fixture = ModList.get().getModContainerById(FIXTURE_ID).get().getMod();
        require(fixture != null && ENTRY_CLASS.equals(fixture.getClass().getName()),
                "Original entrypoint was replaced or renamed");
        Class<?> entry = fixture.getClass();

        ResourceLocation id = new ResourceLocation(FIXTURE_ID, "lifecycle_token");
        require(ForgeRegistries.ITEMS.containsKey(id), "Registered fixture item absent");
        Item item = ForgeRegistries.ITEMS.getValue(id);
        require(item != null && id.equals(ForgeRegistries.ITEMS.getKey(item)), "Wrong registry identity");
        require(ITEM_CLASS.equals(item.getClass().getName()), "Original Item subclass was replaced or renamed");
        int maxStackSize = new ItemStack(item, 1).getMaxStackSize();
        require(maxStackSize == 16, "Native stack size was not preserved");
        int setupEvents = intValue(entry, "setupEventCount");
        int queuedWorkRuns = intValue(entry, "queuedWorkCount");
        require(setupEvents == 1 && queuedWorkRuns == 1, "Common setup or enqueued work count differs");
        Object observedItem = invoke(ownMethod(entry, "observedItem", Item.class));
        boolean sameItem = observedItem == item;
        require(sameItem, "Deferred registry read did not observe the native registered object");
        boolean setupEnabled = booleanValue(entry, "observedEnabled");
        int setupFactor = intValue(entry, "observedFactor");
        require(setupEnabled == expected.enabled && setupFactor == expected.factor,
                "Deferred setup did not observe the expected native config values");

        // Runtime-only lookup of exact fixture-owned classes. No initialize=true or access bypass.
        Class<?> config = Class.forName(CONFIG_CLASS, false, entry.getClassLoader());
        Class<?> commands = Class.forName(COMMAND_CLASS, false, entry.getClassLoader());
        Field specField = config.getDeclaredField("SPEC");
        require(specField.getDeclaringClass() == config && specField.getType() == ForgeConfigSpec.class
                        && Modifier.isPublic(specField.getModifiers()) && Modifier.isStatic(specField.getModifiers()),
                "Unexpected native config spec field");
        Object specValue = specField.get(null);
        require(specValue instanceof ForgeConfigSpec, "Fixture did not retain a native ForgeConfigSpec");
        boolean configLoaded = ((ForgeConfigSpec) specValue).isLoaded();
        require(configLoaded, "Native config has not loaded; defaults alone are not loading proof");
        boolean configEnabled = booleanValue(config, "isEnabled");
        int configFactor = intValue(config, "factor");
        require(configEnabled == expected.enabled && configFactor == expected.factor,
                "Native loaded config disagrees with explicitly supplied probe expectations");
        int registrations = intValue(commands, "registrationCount");
        require(registrations >= 1, "Native RegisterCommandsEvent never reached the fixture subscriber");
        int initialExecutions = intValue(commands, "executionCount");
        require(initialExecutions == 0, "Commands were executed before this controlled probe");

        CommandDispatcher<CommandSource> dispatcher = server.getCommands().getDispatcher();
        CommandSource allowed = server.createCommandSourceStack().withPermission(2);
        CommandSource denied = server.createCommandSourceStack().withPermission(1);
        require(allowed.hasPermission(2) && !denied.hasPermission(2), "Native permission sources differ");
        boolean permissionDenied = rejected(dispatcher, denied, "bridgeprobe 7", commands);
        boolean[] argumentRejections = {
                rejected(dispatcher, allowed, "bridgeprobe -1", commands),
                rejected(dispatcher, allowed, "bridgeprobe 101", commands),
                rejected(dispatcher, allowed, "bridgeprobe text", commands)
        };
        int afterRejections = intValue(commands, "executionCount");
        require(afterRejections == 0, "A rejected command reached the fixture handler");

        int[] inputs = {7, 0, 100};
        int[] results = new int[inputs.length];
        int[] executionCounts = new int[inputs.length];
        for (int index = 0; index < inputs.length; index++) {
            results[index] = dispatcher.execute("bridgeprobe " + inputs[index], allowed);
            executionCounts[index] = intValue(commands, "executionCount");
            int oracle = expected.enabled ? expected.factor * inputs[index] : 0;
            require(results[index] == oracle, "Native command result differs at index " + index);
            require(executionCounts[index] == index + 1, "Native command must invoke the owned handler once");
            require(intValue(commands, "lastInput") == inputs[index]
                            && intValue(commands, "lastResult") == results[index],
                    "Original command observation state was not preserved");
        }
        int lastInput = intValue(commands, "lastInput");
        int lastResult = intValue(commands, "lastResult");
        int finalRegistrations = intValue(commands, "registrationCount");
        require(finalRegistrations == registrations, "The controlled probe unexpectedly rebuilt the dispatcher");

        LinkedHashMap<String, Object> observations = new LinkedHashMap<String, Object>();
        observations.put("schemaVersion", 1);
        observations.put("modId", FIXTURE_ID);
        observations.put("modVersion", fixtureVersion);
        observations.put("registryId", id.toString());
        observations.put("itemClass", item.getClass().getName());
        observations.put("maxStackSize", maxStackSize);
        observations.put("nativeProbeServerThread", serverThread);
        observations.put("setupEvents", setupEvents);
        observations.put("queuedWorkRuns", queuedWorkRuns);
        observations.put("observedItemIdentity", sameItem);
        observations.put("configLoaded", configLoaded);
        observations.put("configEnabled", configEnabled);
        observations.put("configFactor", configFactor);
        observations.put("setupEnabled", setupEnabled);
        observations.put("setupFactor", setupFactor);
        observations.put("registrationCount", registrations);
        observations.put("initialExecutionCount", initialExecutions);
        observations.put("permissionLevel1Rejected", permissionDenied);
        observations.put("argumentRejections", argumentRejections);
        observations.put("afterRejectionsExecutionCount", afterRejections);
        observations.put("commandInputs", inputs);
        observations.put("commandResults", results);
        observations.put("executionCounts", executionCounts);
        observations.put("lastInput", lastInput);
        observations.put("lastResult", lastResult);
        observations.put("finalRegistrationCount", finalRegistrations);
        return BehaviorTraceWriter.render(observations);
    }

    private static boolean rejected(CommandDispatcher<CommandSource> dispatcher, CommandSource source,
                                    String input, Class<?> commands) throws Exception {
        int before = intValue(commands, "executionCount");
        boolean syntaxRejected = false;
        try {
            dispatcher.execute(input, source);
        } catch (CommandSyntaxException expectedNativeRejection) {
            syntaxRejected = true;
        }
        require(syntaxRejected, "Native dispatcher accepted a forbidden command input: " + input);
        require(intValue(commands, "executionCount") == before, "Rejected command executed the owned handler");
        return true;
    }

    /** Reflection is restricted to named, declared, public static zero-argument fixture observations. */
    private static Method ownMethod(Class<?> owner, String name, Class<?> result) throws NoSuchMethodException {
        require(ENTRY_CLASS.equals(owner.getName()) || CONFIG_CLASS.equals(owner.getName())
                        || COMMAND_CLASS.equals(owner.getName()), "Reflection owner is not the controlled fixture");
        Method method = owner.getDeclaredMethod(name);
        require(method.getDeclaringClass() == owner && method.getReturnType() == result
                        && Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers()),
                "Unexpected fixture probe signature: " + name);
        return method;
    }

    private static int intValue(Class<?> owner, String name) throws Exception {
        return ((Integer) invoke(ownMethod(owner, name, int.class))).intValue();
    }

    private static boolean booleanValue(Class<?> owner, String name) throws Exception {
        return ((Boolean) invoke(ownMethod(owner, name, boolean.class))).booleanValue();
    }

    private static Object invoke(Method method) throws Exception {
        try {
            return method.invoke(null);
        } catch (InvocationTargetException wrapped) {
            Throwable cause = wrapped.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("Unexpected reflective invocation failure", wrapped);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
