package dev.legacybridge.validation.logic;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Separate, explicitly authorized runtime QA. Never shipped or invoked by analysis/conversion. */
@Mod(LogicRuntimeProbe.MOD_ID)
public final class LogicRuntimeProbe {
    public static final String MOD_ID = "legacybridge_logic_probe";
    private static final String FIXTURE_ID = "legacybridge_logic";
    private static final String EXPECTED_CLASS = "dev.legacybridge.fixture.logic.LogicItem";
    private static final String BASELINE = "1.20.1-forge-47.4.23";

    public LogicRuntimeProbe() {
        MinecraftForge.EVENT_BUS.addListener(this::serverStarted);
    }

    private void serverStarted(ServerStartedEvent event) {
        Throwable failure = null;
        try {
            String trace = captureBehavior();
            BehaviorTraceWriter.writeNew(Paths.get("logic-trace.json"), trace);
            System.out.println("LEGACYBRIDGE_LOGIC_BEHAVIOR_OK baseline=" + BASELINE
                    + " fixture=legacybridge_logic version=1.0.0 trace=logic-trace.json");
        } catch (Throwable observed) {
            failure = observed;
            System.err.println("LEGACYBRIDGE_LOGIC_BEHAVIOR_FAILED baseline=" + BASELINE);
            observed.printStackTrace(System.err);
        } finally {
            try {
                event.getServer().execute(() -> event.getServer().halt(false));
            } catch (Throwable shutdownFailure) {
                System.err.println("LEGACYBRIDGE_LOGIC_BEHAVIOR_FAILED shutdown baseline=" + BASELINE);
                shutdownFailure.printStackTrace(System.err);
                if (failure != null) shutdownFailure.addSuppressed(failure);
                throw new IllegalStateException("Cannot schedule orderly probe shutdown", shutdownFailure);
            }
        }
        if (failure != null) throw new IllegalStateException("Logic runtime comparison probe failed", failure);
    }

    private static String captureBehavior() throws Exception {
        require(ModList.get().getModContainerById(FIXTURE_ID).isPresent(), "Fixture mod not loaded");
        String fixtureVersion = ModList.get().getModContainerById(FIXTURE_ID).get()
                .getModInfo().getVersion().toString();
        require("1.0.0".equals(fixtureVersion), "Unexpected fixture version");
        ResourceLocation id = new ResourceLocation(FIXTURE_ID, "parity_token");
        require(ForgeRegistries.ITEMS.containsKey(id), "Registered fixture item absent");
        Item item = ForgeRegistries.ITEMS.getValue(id);
        require(item != null && id.equals(ForgeRegistries.ITEMS.getKey(item)), "Wrong registry identity");
        Class<?> ownClass = item.getClass();
        require(EXPECTED_CLASS.equals(ownClass.getName()), "Original Item subclass was replaced or renamed");

        Method score = ownMethod(ownClass, "score", false, int.class);
        Method callCount = ownMethod(ownClass, "callCount", false);
        Method instances = ownMethod(ownClass, "ruleInstances", true);
        Method seed = ownMethod(ownClass, "helperSeed", true);
        Method initializations = ownMethod(ownClass, "helperInitializationCount", true);

        int initialCalls = invokeInt(callCount, item);
        int initialInstances = invokeInt(instances, null);
        require(initialCalls == 0, "Unexpected score calls before the controlled server probe");
        require(initialInstances == 1, "The single registered item must construct exactly one rule");
        int observedSeed = invokeInt(seed, null);
        int initialInitializationCount = invokeInt(initializations, null);
        require(observedSeed == 7 && initialInitializationCount == 1, "Incorrect one-time helper initialization");
        int maxStackSize = new ItemStack(item, 1).getMaxStackSize();
        require(maxStackSize == 16, "Native stack size was not preserved");

        int[] inputs = {-3, 0, 1, 2, 3, 8, 16, 31, 64};
        int[] expectedScores = {-1002, 17, 22, 27, 32, 33, 49, 88, 145};
        int[] scores = new int[inputs.length];
        int[] scoreCalls = new int[inputs.length];
        for (int index = 0; index < inputs.length; index++) {
            scores[index] = invokeInt(score, item, inputs[index]);
            scoreCalls[index] = invokeInt(callCount, item);
            require(scores[index] == expectedScores[index], "Score oracle mismatch at index " + index);
            require(scoreCalls[index] == index + 1, "Exception/interface/inheritance call state was not preserved");
        }

        int[] counts = {1, 2, 3, 8, 16, 31, 64};
        boolean[] expectedFoil = {true, false, true, false, false, true, false};
        boolean[] foil = new boolean[counts.length];
        int[] foilCalls = new int[counts.length];
        for (int index = 0; index < counts.length; index++) {
            ItemStack stack = new ItemStack(item, counts[index]);
            require(stack.getCount() == counts[index], "Native stack input count changed");
            foil[index] = stack.hasFoil();
            foilCalls[index] = invokeInt(callCount, item);
            require(foil[index] == expectedFoil[index], "Native ItemStack-to-Item virtual dispatch differs");
            require(foilCalls[index] == inputs.length + index + 1, "Native glint hook did not invoke the original rule exactly once");
        }

        int beforeEnchantedCalls = invokeInt(callCount, item);
        ItemStack enchanted = new ItemStack(item, 2);
        enchanted.enchant(Enchantments.UNBREAKING, 1);
        boolean enchantedFoil = enchanted.hasFoil();
        int afterEnchantedCalls = invokeInt(callCount, item);
        require(enchantedFoil, "Native superclass foil branch was not retained");
        require(afterEnchantedCalls == beforeEnchantedCalls, "Superclass-true path lost original short-circuit behavior");
        int finalInstances = invokeInt(instances, null);
        int finalSeed = invokeInt(seed, null);
        int finalInitializations = invokeInt(initializations, null);
        require(finalInstances == 1 && finalSeed == 7 && finalInitializations == 1,
                "Static or constructor state changed during repeated calls");

        LinkedHashMap<String, Object> observations = new LinkedHashMap<String, Object>();
        observations.put("schemaVersion", 1);
        observations.put("modId", FIXTURE_ID);
        observations.put("modVersion", fixtureVersion);
        observations.put("registryId", id.toString());
        observations.put("itemClass", ownClass.getName());
        observations.put("maxStackSize", maxStackSize);
        observations.put("initialRuleCalls", initialCalls);
        observations.put("initialRuleInstances", initialInstances);
        observations.put("helperSeed", observedSeed);
        observations.put("initialHelperInitializations", initialInitializationCount);
        observations.put("scoreInputs", inputs);
        observations.put("scores", scores);
        observations.put("scoreCalls", scoreCalls);
        observations.put("foilStackCounts", counts);
        observations.put("foil", foil);
        observations.put("foilCalls", foilCalls);
        observations.put("enchantedFoil", enchantedFoil);
        observations.put("beforeEnchantedCalls", beforeEnchantedCalls);
        observations.put("afterEnchantedCalls", afterEnchantedCalls);
        observations.put("finalRuleInstances", finalInstances);
        observations.put("finalHelperSeed", finalSeed);
        observations.put("finalHelperInitializations", finalInitializations);
        return BehaviorTraceWriter.render(observations);
    }

    /** Reflection is confined to already-loaded exact fixture-owned public int methods. */
    private static Method ownMethod(Class<?> owner, String name, boolean isStatic, Class<?>... parameters)
            throws NoSuchMethodException {
        require(EXPECTED_CLASS.equals(owner.getName()), "Reflection owner is not the controlled fixture");
        Method method = owner.getDeclaredMethod(name, parameters);
        require(method.getDeclaringClass() == owner && method.getReturnType() == int.class
                        && Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers()) == isStatic,
                "Unexpected fixture probe signature: " + name);
        return method;
    }

    private static int invokeInt(Method method, Object receiver, Object... arguments) throws Exception {
        try {
            Object result = method.invoke(receiver, arguments);
            require(result instanceof Integer, "Fixture probe did not return an int");
            return ((Integer) result).intValue();
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
