package dev.legacybridge.validation.composition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Collects delegated observations; it contains no copy of the expected behavior oracle. */
public final class BehaviorCapture {
    private BehaviorCapture() { }
    public interface ItemView {
        String registryId(); String className(); int stackLimit(); int calls() throws Exception;
        StackView stack(int count);
    }
    public interface StackView { int count(); boolean foil(); boolean enchanted(); void enchant(); }
    public interface FurnaceFactory { FurnaceView open() throws Exception; }
    public interface FurnaceView extends AutoCloseable {
        Map<String, Object> recipe() throws Exception;
        Map<String, Object> sample(int tick) throws Exception;
        void tick() throws Exception;
        @Override void close() throws Exception;
    }
    public static Map<String, Object> capture(ItemView item, FurnaceFactory factory) throws Exception {
        Map<String, Object> result = ProbeJson.object("registryId", item.registryId(), "itemClass", item.className(),
                "maxStackSize", item.stackLimit(), "initialCalls", item.calls());
        List<Map<String, Object>> foil = new ArrayList<Map<String, Object>>();
        for (int count = 1; count <= 4; count++) {
            StackView stack = item.stack(count);
            int before = item.calls(); boolean observed = stack.foil(); int after = item.calls();
            foil.add(ProbeJson.object("count", stack.count(), "foil", observed, "callsBefore", before, "callsAfter", after));
        }
        result.put("foil", foil);
        StackView enchanted = item.stack(2); enchanted.enchant();
        int before = item.calls(); boolean observed = enchanted.foil(); int after = item.calls();
        result.put("enchanted", ProbeJson.object("count", enchanted.count(), "enchantment", "minecraft:unbreaking",
                "level", 1, "isEnchanted", enchanted.enchanted(), "foil", observed, "callsBefore", before, "callsAfter", after));
        try (FurnaceView furnace = factory.open()) {
            Map<String, Object> recipe = new LinkedHashMap<String, Object>(furnace.recipe());
            if (recipe.containsKey("samples")) throw new IllegalArgumentException("OBS_RECIPE_SAMPLES");
            List<Map<String, Object>> samples = new ArrayList<Map<String, Object>>();
            samples.add(new LinkedHashMap<String, Object>(furnace.sample(0)));
            for (int tick = 1; tick <= 120; tick++) {
                furnace.tick();
                samples.add(new LinkedHashMap<String, Object>(furnace.sample(tick)));
            }
            recipe.put("samples", samples);
            result.put("cooking", recipe);
        }
        return result;
    }
}
