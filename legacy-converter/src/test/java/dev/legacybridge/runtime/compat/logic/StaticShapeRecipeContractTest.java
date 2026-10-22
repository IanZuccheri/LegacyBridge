package dev.legacybridge.runtime.compat.logic;

import dev.legacybridge.runtime.compat.UnsupportedCompatibilityException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** JDK-only adapter contract validation, not original-mod or game execution. */
class StaticShapeRecipeContractTest {
    @Test void exactRecipeIsDeeplyImmutableAndHasNoInheritedFullCubePermission() {
        var recipe=StaticShapeBlockArtifactAdapter.stonePropertiesCalls();
        assertEquals(PlainBlockArtifactAdapter.stonePropertiesCalls(),recipe);
        assertDoesNotThrow(()->StaticShapeBlockArtifactAdapter.validateStonePropertiesCalls(recipe));
        assertThrows(UnsupportedOperationException.class,()->recipe.clear());
        for(var call:recipe)
            assertThrows(UnsupportedOperationException.class,()->call.put("descriptor","()V"));
    }

    @ParameterizedTest @ValueSource(strings={"kind","owner","name","descriptor","extra","missing",
        "order","additional","removed","nullList","nullEntry"})
    void sharedRecipeDriftRequiresIndependentReview(String drift) {
        var recipe=new ArrayList<Map<String,String>>();
        for(var call:PlainBlockArtifactAdapter.stonePropertiesCalls())recipe.add(new HashMap<>(call));
        switch(drift) {
            case "kind","owner","name","descriptor" -> recipe.get(0).put(drift,"unreviewed");
            case "extra" -> recipe.get(0).put("semanticHint","guessed");
            case "missing" -> recipe.get(0).remove("owner");
            case "order" -> Collections.swap(recipe,1,3);
            case "additional" -> recipe.add(new HashMap<>(recipe.get(0)));
            case "removed" -> recipe.remove(4);
            case "nullEntry" -> recipe.set(2,null);
            case "nullList" -> { }
            default -> throw new AssertionError(drift);
        }
        var failure=assertThrows(UnsupportedCompatibilityException.class,
            ()->StaticShapeBlockArtifactAdapter.validateStonePropertiesCalls(drift.equals("nullList")?null:recipe));
        assertTrue(failure.getMessage().contains("independent review"));
    }
}

