package dev.legacybridge.converter.conversion;

import dev.legacybridge.runtime.compat.AdapterCatalog;
import dev.legacybridge.runtime.compat.UnsupportedCompatibilityException;
import dev.legacybridge.runtime.compat.logic.FoodContractAdapter;
import dev.legacybridge.runtime.compat.logic.PreservedLogicAdapter;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoodContractAdapterTest {
    private static final String OLD="net/minecraft/item/Food", TARGET="net/minecraft/world/food/FoodProperties";
    @Test void exactStructuralComponentsPreservePrimitivesArraysAndArgumentOrder() {
        var classes=Map.of(OLD,TARGET,OLD+"$Builder",TARGET+"$Builder");
        assertEquals("(I[F)L"+TARGET+"$Builder;",FoodContractAdapter.remapDescriptor("(I[F)L"+OLD+"$Builder;",classes));
        assertEquals("[[L"+TARGET+";",FoodContractAdapter.remapDescriptor("[[L"+OLD+";",classes));
        assertEquals("(Lunknown/Api;)V",FoodContractAdapter.remapDescriptor("(Lunknown/Api;)V",classes),
                "A descriptor operation is not permission to retain an unknown reference; the compiler must reject it");
    }
    @Test void similarNameAndGlobalErasureAreNotFoodEquivalence() {
        assertThrows(UnsupportedCompatibilityException.class,()->FoodContractAdapter.remapDescriptor("L"+OLD+";",Map.of(OLD,"java/lang/Object")));
        assertThrows(UnsupportedCompatibilityException.class,()->FoodContractAdapter.remapDescriptor("L"+OLD+"$Builder;",Map.of(OLD+"$Builder",TARGET)));
        var nullValue=new HashMap<String,String>();nullValue.put(OLD,null);
        assertThrows(UnsupportedCompatibilityException.class,()->FoodContractAdapter.remapDescriptor("L"+OLD+";",nullValue));
    }
    @Test void invalidDescriptorsAndMissingMapsFailExplicitly() {
        assertThrows(UnsupportedCompatibilityException.class,()->FoodContractAdapter.remapDescriptor("()V",null));
        assertThrows(UnsupportedCompatibilityException.class,()->FoodContractAdapter.remapDescriptor(null,Map.of()));
        assertThrows(UnsupportedCompatibilityException.class,()->FoodContractAdapter.remapDescriptor("(Lbroken)V",Map.of()));
        assertThrows(UnsupportedCompatibilityException.class,()->FoodContractAdapter.remapDescriptor("[V",Map.of()));
    }
    @Test void ownMarkerAndBindingsDoNotModifyEarlierProfilesOrRuntimeIdentity() throws Exception {
        var manifest=new Manifest(new ByteArrayInputStream(FoodContractAdapter.targetManifest("1.0.0")));
        assertEquals("food-item-v1",manifest.getMainAttributes().getValue("LegacyBridge-Profile"));
        assertEquals("1.0.0",manifest.getMainAttributes().getValue("Implementation-Version"));
        assertEquals("logic-preserving-v1",new Manifest(new ByteArrayInputStream(PreservedLogicAdapter.targetManifest("1.0.0"))).getMainAttributes().getValue("LegacyBridge-Profile"));
        assertEquals(TransformationBindings.forFood().bindingTree(),FoodContractAdapter.bindingTree());
        assertEquals(AdapterCatalog.RUNTIME_VERSION,FoodContractAdapter.bindingTree().get("runtimeVersion"));
        assertEquals(1,AdapterCatalog.require(AdapterCatalog.FOOD).revision());
        assertTrue(AdapterCatalog.require(AdapterCatalog.FOOD).behaviorDifferences().contains("ITEM_INTERACT_START"));
        assertTrue(AdapterCatalog.require(AdapterCatalog.FOOD).knownLimitations().contains("remainder"));
    }
}
