package dev.legacybridge.validation.composition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Synthetic Java delegates verify capture orchestration only; they are not native gameplay evidence. */
final class BehaviorCaptureTest {
    @Test void invokesFourPlainStacksAndOneSeparateEnchantedStackExactlyOnce() throws Exception {
        FakeItem item = new FakeItem();
        Map<String, Object> result = BehaviorCapture.capture(item, () -> new FakeFurnace());
        assertEquals(Arrays.asList(1, 2, 3, 4, 2), item.requestedCounts);
        assertEquals(5, item.stacks.size());
        for (int index = 0; index < item.stacks.size(); index++) {
            assertEquals(1, item.stacks.get(index).foilInvocations);
            assertEquals(index == 4 ? 1 : 0, item.stacks.get(index).enchantInvocations);
        }
        assertEquals(4, foil(result).size());
        assertEquals(1, item.stacks.get(4).enchantmentReads);
        assertEquals(11, item.callReads);
    }

    @Test void retainsDelegatedIdentityStackLimitAndObservedCountsInsteadOfOracleValues() throws Exception {
        FakeItem item = new FakeItem();
        item.countOffset = 20;
        Map<String, Object> result = BehaviorCapture.capture(item, () -> new FakeFurnace());
        assertEquals("synthetic:unexpected_item", result.get("registryId"));
        assertEquals("synthetic.OtherClass", result.get("itemClass"));
        assertEquals(7, result.get("maxStackSize"));
        for (int index = 0; index < 4; index++) assertEquals(index + 21, foil(result).get(index).get("count"));
        assertEquals(22, map(result.get("enchanted")).get("count"));
    }

    @Test void recordsWrongFoilAndHelperResultsVerbatimForTheIndependentVerifier() throws Exception {
        FakeItem item = new FakeItem();
        Map<String, Object> result = BehaviorCapture.capture(item, () -> new FakeFurnace());
        assertEquals(13, result.get("initialCalls"));
        int[] before = {13, 16, 16, 18}, after = {16, 16, 18, 27};
        boolean[] nativeValues = {false, true, false, true};
        for (int index = 0; index < 4; index++) {
            assertEquals(nativeValues[index], foil(result).get(index).get("foil"));
            assertEquals(before[index], foil(result).get(index).get("callsBefore"));
            assertEquals(after[index], foil(result).get(index).get("callsAfter"));
        }
        Map<String, Object> enchanted = map(result.get("enchanted"));
        assertEquals(false, enchanted.get("foil"));
        assertEquals(27, enchanted.get("callsBefore"));
        assertEquals(32, enchanted.get("callsAfter"));
    }

    @Test void recordsActuallyObservedEnchantmentBooleanEvenWhenEnchantDidNotTakeEffect() throws Exception {
        FakeItem item = new FakeItem();
        item.enchantWorks = false;
        Map<String, Object> enchanted = map(BehaviorCapture.capture(item, () -> new FakeFurnace()).get("enchanted"));
        assertEquals(false, enchanted.get("isEnchanted"));
        assertEquals("minecraft:unbreaking", enchanted.get("enchantment"));
        assertEquals(1, enchanted.get("level"));
        assertEquals(1, item.stacks.get(4).enchantInvocations);
    }

    @Test void recordsEnchantmentAfterDelegatingItsActualOperation() throws Exception {
        FakeItem item = new FakeItem();
        Map<String, Object> enchanted = map(BehaviorCapture.capture(item, () -> new FakeFurnace()).get("enchanted"));
        assertEquals(true, enchanted.get("isEnchanted"));
        assertTrue(item.stacks.get(4).enchantCalledBeforeFoil);
        assertFalse(item.stacks.get(0).enchantCalledBeforeFoil);
    }

    @Test void delegatesExactly120TicksAnd121SamplesInOrderThenClosesOnce() throws Exception {
        FakeFurnace furnace = new FakeFurnace();
        List<Map<String, Object>> samples = samples(BehaviorCapture.capture(new FakeItem(), () -> furnace));
        assertEquals(120, furnace.ticks);
        assertEquals(121, furnace.sampleCalls);
        assertEquals(121, samples.size());
        assertEquals(1, furnace.closeCalls);
        List<String> expected = new ArrayList<String>();
        expected.add("recipe"); expected.add("sample-0");
        for (int tick = 1; tick <= 120; tick++) {
            expected.add("tick-" + tick); expected.add("sample-" + tick);
            assertEquals(tick, samples.get(tick).get("tick"));
            assertEquals(tick, samples.get(tick).get("outputCount"));
        }
        expected.add("close");
        assertEquals(expected, furnace.events);
        assertEquals(0, samples.get(0).get("outputCount"));
    }

    @Test void freezesEachFlatSampleWhenDelegateReusesAndMutatesTheSameMap() throws Exception {
        FakeFurnace furnace = new FakeFurnace();
        List<Map<String, Object>> samples = samples(BehaviorCapture.capture(new FakeItem(), () -> furnace));
        for (int tick = 0; tick <= 120; tick++) {
            assertEquals(tick, samples.get(tick).get("tick"));
            assertNotSame(furnace.reusedSample, samples.get(tick));
            if (tick > 0) assertNotSame(samples.get(tick - 1), samples.get(tick));
        }
        furnace.reusedSample.put("tick", -99);
        assertEquals(0, samples.get(0).get("tick"));
        assertEquals(120, samples.get(120).get("tick"));
    }

    @Test void copiesRecipeTopLevelAndPreservesDeliberatelyUnexpectedNativeMetadata() throws Exception {
        FakeFurnace furnace = new FakeFurnace();
        Map<String, Object> cooking = map(BehaviorCapture.capture(new FakeItem(), () -> furnace).get("cooking"));
        assertNotSame(furnace.recipe, cooking);
        assertEquals("synthetic:other_recipe", cooking.get("recipeId"));
        assertEquals(19, cooking.get("recipeTicks"));
        assertEquals(3.5F, cooking.get("recipeExperience"));
        assertFalse(furnace.recipe.containsKey("samples"));
        furnace.recipe.put("recipeTicks", -5);
        assertEquals(19, cooking.get("recipeTicks"));
    }

    @Test void rejectsRecipeTryingToSupplyItsOwnSamplesAndStillCloses() {
        FakeFurnace furnace = new FakeFurnace();
        furnace.recipe.put("samples", Collections.emptyList());
        IllegalArgumentException failure = assertNoReturn(IllegalArgumentException.class, furnace);
        assertEquals("OBS_RECIPE_SAMPLES", failure.getMessage());
        assertEquals(Arrays.asList("recipe", "close"), furnace.events);
        assertEquals(0, furnace.ticks);
        assertEquals(0, furnace.sampleCalls);
        assertEquals(1, furnace.closeCalls);
    }

    @Test void closesAfterRecipeFailureWithoutSamplesTicksOrPartialResult() {
        FakeFurnace furnace = new FakeFurnace();
        furnace.recipeFailure = new IOException("synthetic-recipe");
        assertSame(furnace.recipeFailure, assertNoReturn(IOException.class, furnace));
        assertEquals(Arrays.asList("recipe", "close"), furnace.events);
        assertEquals(1, furnace.closeCalls);
    }

    @Test void closesAfterInitialSampleFailureWithoutAnyTick() {
        FakeFurnace furnace = new FakeFurnace();
        furnace.failSampleAt = 0;
        assertSame(furnace.sampleFailure, assertNoReturn(IOException.class, furnace));
        assertEquals(0, furnace.ticks);
        assertEquals(1, furnace.sampleCalls);
        assertEquals(Arrays.asList("recipe", "sample-0", "close"), furnace.events);
    }

    @Test void tickFailureStopsImmediatelyBeforeItsSampleAndClosesWithoutPartialResult() {
        FakeFurnace furnace = new FakeFurnace();
        furnace.failTickAt = 5;
        assertSame(furnace.tickFailure, assertNoReturn(IOException.class, furnace));
        assertEquals(5, furnace.ticks);
        assertEquals(5, furnace.sampleCalls);
        assertEquals("tick-5", furnace.events.get(furnace.events.size() - 2));
        assertEquals("close", furnace.events.get(furnace.events.size() - 1));
        assertEquals(1, furnace.closeCalls);
    }

    @Test void laterSampleFailureNeverDrivesAnotherTick() {
        FakeFurnace furnace = new FakeFurnace();
        furnace.failSampleAt = 17;
        assertSame(furnace.sampleFailure, assertNoReturn(IOException.class, furnace));
        assertEquals(17, furnace.ticks);
        assertEquals(18, furnace.sampleCalls);
        assertEquals("sample-17", furnace.events.get(furnace.events.size() - 2));
        assertEquals("close", furnace.events.get(furnace.events.size() - 1));
        assertEquals(1, furnace.closeCalls);
    }

    @Test void normalCaptureWithFailingCloseDoesNotReturnAnyResult() {
        FakeFurnace furnace = new FakeFurnace();
        furnace.closeFailure = new IOException("synthetic-close");
        assertSame(furnace.closeFailure, assertNoReturn(IOException.class, furnace));
        assertEquals(120, furnace.ticks);
        assertEquals(121, furnace.sampleCalls);
        assertEquals(1, furnace.closeCalls);
    }

    @Test void retainsRecipeFailureAndSuppressesCloseFailure() {
        FakeFurnace furnace = new FakeFurnace();
        furnace.recipeFailure = new IOException("synthetic-recipe");
        furnace.closeFailure = new IOException("synthetic-close");
        IOException failure = assertNoReturn(IOException.class, furnace);
        assertSame(furnace.recipeFailure, failure);
        assertSuppressed(failure, furnace.closeFailure);
        assertEquals(1, furnace.closeCalls);
    }

    @Test void retainsTickFailureAndSuppressesCloseFailure() {
        FakeFurnace furnace = new FakeFurnace();
        furnace.failTickAt = 1;
        furnace.closeFailure = new IOException("synthetic-close");
        IOException failure = assertNoReturn(IOException.class, furnace);
        assertSame(furnace.tickFailure, failure);
        assertSuppressed(failure, furnace.closeFailure);
        assertEquals(1, furnace.ticks);
        assertEquals(1, furnace.sampleCalls);
    }

    @Test void retainsSampleFailureAndSuppressesCloseFailure() {
        FakeFurnace furnace = new FakeFurnace();
        furnace.failSampleAt = 120;
        furnace.closeFailure = new IOException("synthetic-close");
        IOException failure = assertNoReturn(IOException.class, furnace);
        assertSame(furnace.sampleFailure, failure);
        assertSuppressed(failure, furnace.closeFailure);
        assertEquals(120, furnace.ticks);
        assertEquals(121, furnace.sampleCalls);
    }

    @Test void rejectsNullRecipeAndStillClosesWithoutTicking() {
        FakeFurnace furnace = new FakeFurnace();
        furnace.nullRecipe = true;
        assertNoReturn(NullPointerException.class, furnace);
        assertEquals(Arrays.asList("recipe", "close"), furnace.events);
    }

    @Test void rejectsNullSampleAndStillClosesWithoutTicking() {
        FakeFurnace furnace = new FakeFurnace();
        furnace.nullSample = true;
        assertNoReturn(NullPointerException.class, furnace);
        assertEquals(Arrays.asList("recipe", "sample-0", "close"), furnace.events);
    }

    @Test void factoryFailurePropagatesWithoutPublishingTheAlreadyCollectedFoilValues() {
        IOException expected = new IOException("synthetic-open");
        AtomicReference<Map<String, Object>> returned = new AtomicReference<Map<String, Object>>();
        AtomicInteger opens = new AtomicInteger();
        assertSame(expected, assertThrows(IOException.class, () -> returned.set(BehaviorCapture.capture(new FakeItem(), () -> {
            opens.incrementAndGet(); throw expected;
        }))));
        assertEquals(1, opens.get());
        assertNull(returned.get());
    }

    @Test void foilFailurePreventsFurnaceAcquisitionAndPartialResult() {
        FakeItem item = new FakeItem();
        item.failFoilAt = 2;
        AtomicInteger opens = new AtomicInteger();
        AtomicReference<Map<String, Object>> returned = new AtomicReference<Map<String, Object>>();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> returned.set(BehaviorCapture.capture(item, () -> {
                    opens.incrementAndGet(); return new FakeFurnace();
                })));
        assertSame(item.foilFailure, failure);
        assertEquals(0, opens.get());
        assertEquals(Arrays.asList(1, 2, 3), item.requestedCounts);
        assertNull(returned.get());
    }

    private static <T extends Throwable> T assertNoReturn(Class<T> type, FakeFurnace furnace) {
        AtomicReference<Map<String, Object>> returned = new AtomicReference<Map<String, Object>>();
        T failure = assertThrows(type,
                () -> returned.set(BehaviorCapture.capture(new FakeItem(), () -> furnace)));
        assertNull(returned.get(), "Capture must not expose a partial result on failure");
        return failure;
    }
    private static void assertSuppressed(Throwable primary, Throwable expected) {
        assertEquals(1, primary.getSuppressed().length);
        assertSame(expected, primary.getSuppressed()[0]);
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
    @SuppressWarnings("unchecked") private static List<Map<String, Object>> foil(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("foil");
    }
    @SuppressWarnings("unchecked") private static List<Map<String, Object>> samples(Map<String, Object> result) {
        return (List<Map<String, Object>>) map(result.get("cooking")).get("samples");
    }

    private static final class FakeItem implements BehaviorCapture.ItemView {
        final List<Integer> requestedCounts = new ArrayList<Integer>();
        final List<FakeStack> stacks = new ArrayList<FakeStack>();
        final int[] deltas = {3, 0, 2, 9, 5};
        final boolean[] foils = {false, true, false, true, false};
        final IllegalStateException foilFailure = new IllegalStateException("synthetic-foil");
        int helperCalls = 13, callReads, countOffset, failFoilAt = -1;
        boolean enchantWorks = true;
        @Override public String registryId() { return "synthetic:unexpected_item"; }
        @Override public String className() { return "synthetic.OtherClass"; }
        @Override public int stackLimit() { return 7; }
        @Override public int calls() { callReads++; return helperCalls; }
        @Override public BehaviorCapture.StackView stack(int count) {
            requestedCounts.add(count);
            FakeStack stack = new FakeStack(this, stacks.size(), count + countOffset);
            stacks.add(stack);
            return stack;
        }
    }
    private static final class FakeStack implements BehaviorCapture.StackView {
        final FakeItem owner;
        final int index, actualCount;
        int foilInvocations, enchantInvocations, enchantmentReads;
        boolean enchanted, enchantCalledBeforeFoil;
        FakeStack(FakeItem owner, int index, int count) { this.owner = owner; this.index = index; actualCount = count; }
        @Override public int count() { return actualCount; }
        @Override public boolean foil() {
            foilInvocations++;
            enchantCalledBeforeFoil = enchantInvocations > 0;
            if (owner.failFoilAt == index) throw owner.foilFailure;
            owner.helperCalls += owner.deltas[index];
            return owner.foils[index];
        }
        @Override public boolean enchanted() { enchantmentReads++; return enchanted; }
        @Override public void enchant() { enchantInvocations++; enchanted = owner.enchantWorks; }
    }
    private static final class FakeFurnace implements BehaviorCapture.FurnaceView {
        final List<String> events = new ArrayList<String>();
        final Map<String, Object> reusedSample = new LinkedHashMap<String, Object>();
        final Map<String, Object> recipe = ProbeJson.object("recipeId", "synthetic:other_recipe",
                "recipeType", "synthetic:other_type", "ingredientTag", "synthetic:other_tag",
                "tagItems", Collections.singletonList("synthetic:other_input"),
                "recipeTicks", 19, "recipeExperience", 3.5F);
        final IOException tickFailure = new IOException("synthetic-tick"), sampleFailure = new IOException("synthetic-sample");
        IOException recipeFailure, closeFailure;
        int ticks, sampleCalls, closeCalls, failTickAt = -1, failSampleAt = -1;
        boolean nullRecipe, nullSample;
        @Override public Map<String, Object> recipe() throws IOException {
            events.add("recipe");
            if (recipeFailure != null) throw recipeFailure;
            return nullRecipe ? null : recipe;
        }
        @Override public Map<String, Object> sample(int tick) throws IOException {
            events.add("sample-" + tick); sampleCalls++;
            assertEquals(ticks, tick, "Each sample must follow exactly that many delegated ticks");
            if (tick == failSampleAt) throw sampleFailure;
            if (nullSample) return null;
            reusedSample.clear();
            reusedSample.putAll(ProbeJson.object("tick", tick,
                    "inputId", "synthetic:input", "inputCount", 777 - tick,
                    "fuelId", "synthetic:fuel", "fuelCount", 10,
                    "outputId", "synthetic:output", "outputCount", ticks));
            return reusedSample;
        }
        @Override public void tick() throws IOException {
            ticks++; events.add("tick-" + ticks);
            if (ticks == failTickAt) throw tickFailure;
        }
        @Override public void close() throws IOException {
            closeCalls++; events.add("close");
            if (closeFailure != null) throw closeFailure;
        }
    }
}
